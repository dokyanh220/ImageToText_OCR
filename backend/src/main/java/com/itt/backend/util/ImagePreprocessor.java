package com.itt.backend.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Component;

import com.itt.backend.service.OcrMode;

@Component
public class ImagePreprocessor {

    static {
        try {
            // Thử load theo cách tiêu chuẩn của org.openpnp
            nu.pattern.OpenCV.loadShared();
        } catch (Throwable e) {
            try {
                // Fallback cho Java >= 12
                nu.pattern.OpenCV.loadLocally();
            } catch (Throwable e2) {
                System.err.println("Không thể load OpenCV: " + e2.getMessage());
            }
        }
    }

    public File preprocess(File input) throws Exception {
        // Giữ nguyên hành vi cũ (FAST) để không phá code hiện tại
        return preprocess(input, OcrMode.FAST).getFirst();
    }

    /**
     * Preprocess theo mode.
     * - FAST: pipeline cũ (ổn định, nhanh).
     * - ACCURATE: pipeline nâng cao (banner màu, font cách điệu, tiếng Việt có dấu).
     *
     * Trả về danh sách phiên bản ảnh đã xử lý (ví dụ normal + inverted cho banner).
     */
    public List<File> preprocess(File input, OcrMode mode) throws Exception {
        return preprocessInternal(input, mode);
    }

    private List<File> preprocessInternal(File input, OcrMode mode) throws Exception {

        Mat img = Imgcodecs.imread(input.getAbsolutePath());

        if (img.empty()) {
            throw new RuntimeException("Không đọc được ảnh!");
        }

        try {
            if (mode == OcrMode.FAST) {
                return List.of(preprocessFast(img, input));
            }

            // ACCURATE: pipeline nâng cao
            boolean bannerLike = isStrongColorBackground(img);

            Mat gray = new Mat();
            Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);

            // 1) Resize 2x hoặc 3x (tự chọn theo kích thước)
            double scale = chooseScale(gray);
            Mat resized = new Mat();
            Imgproc.resize(gray, resized, new Size(), scale, scale, Imgproc.INTER_CUBIC);

            // 2) Histogram equalization (tăng contrast)
            Mat equalized = new Mat();
            Imgproc.equalizeHist(resized, equalized);

            // 3) Gaussian blur (denoise)
            Mat blurred = new Mat();
            Imgproc.GaussianBlur(equalized, blurred, new Size(3, 3), 0.8);

            // 4) Adaptive threshold (ổn hơn cho banner/màu/gradient)
            Mat binary = new Mat();
            Imgproc.adaptiveThreshold(
                    blurred,
                    binary,
                    255,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY,
                    31,
                    5);

            // 5) Auto detect nền sáng/tối: đảm bảo chữ đen nền trắng
            if (Core.mean(binary).val[0] < 127) {
                Core.bitwise_not(binary, binary);
            }

            // 6) Morphology closing để liền nét chữ (đặc biệt font cách điệu)
            Mat closed = new Mat();
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2));
            Imgproc.morphologyEx(binary, closed, Imgproc.MORPH_CLOSE, kernel);

            // 7) Optional deskew (nhẹ, chỉ khi nghiêng đáng kể)
            Mat deskewed = deskewIfNeeded(closed);

            List<File> outputs = new ArrayList<>();
            outputs.add(writeTempLikeInput(input, deskewed, "processed_"));

            // 8) Tối ưu cho banner: thử thêm phiên bản inverted nếu ảnh có nền màu mạnh
            if (bannerLike) {
                Mat inverted = new Mat();
                Core.bitwise_not(deskewed, inverted);
                outputs.add(writeTempLikeInput(input, inverted, "processed_inv_"));
                inverted.release();
            }

            // cleanup
            gray.release();
            resized.release();
            equalized.release();
            blurred.release();
            binary.release();
            closed.release();
            if (deskewed != closed) {
                deskewed.release();
            }
            kernel.release();

            return outputs;
        } finally {
            img.release();
        }
    }

    private File preprocessFast(Mat imgBgr, File input) throws Exception {
        // 1. Chuyển sang ảnh xám (Grayscale)
        Mat gray = new Mat();
        Imgproc.cvtColor(imgBgr, gray, Imgproc.COLOR_BGR2GRAY);

        // 2. Phóng to ảnh vừa phải (2.5x) để cân bằng giữa độ nét và noise
        Mat resized = new Mat();
        Imgproc.resize(gray, resized, new Size(), 2.5, 2.5, Imgproc.INTER_CUBIC);

        // 3. Khử nhiễu nhẹ
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(resized, blurred, new Size(3, 3), 0.5);

        // 4. Nhị phân hóa bằng thuật toán Otsu
        Mat binary = new Mat();
        Imgproc.threshold(blurred, binary, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);

        // 5. Kiểm tra độ phân cực: chữ ĐEN trên nền TRẮNG
        Scalar mean = Core.mean(binary);
        if (mean.val[0] < 127) {
            Core.bitwise_not(binary, binary);
        }

        File output = writeTempLikeInput(input, binary, "processed_");

        gray.release();
        resized.release();
        blurred.release();
        binary.release();

        return output;
    }

    private File writeTempLikeInput(File input, Mat mat, String prefix) throws Exception {
        String name = input.getName();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf(".")) : ".png";
        File output = File.createTempFile(prefix, ext);
        Imgcodecs.imwrite(output.getAbsolutePath(), mat);
        return output;
    }

    private double chooseScale(Mat gray) {
        int w = gray.width();
        int h = gray.height();
        int min = Math.min(w, h);
        if (min < 600) return 3.0;
        if (min < 1200) return 2.0;
        return 1.5;
    }

    /**
     * Heuristic: banner nền màu mạnh thường có saturation cao & độ phân tán màu lớn.
     * Dùng HSV để đo mean S và stddev S.
     */
    private boolean isStrongColorBackground(Mat bgr) {
        Mat hsv = new Mat();
        Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV);

        List<Mat> channels = new ArrayList<>(3);
        Core.split(hsv, channels);
        Mat s = channels.get(1);

        MatOfDouble mean = new MatOfDouble();
        MatOfDouble std = new MatOfDouble();
        Core.meanStdDev(s, mean, std);
        double sMean = mean.toArray().length > 0 ? mean.toArray()[0] : 0.0;
        double sStd = std.toArray().length > 0 ? std.toArray()[0] : 0.0;

        // cleanup
        hsv.release();
        for (Mat c : channels) c.release();
        mean.release();
        std.release();

        // Ngưỡng thực nghiệm (có thể tinh chỉnh theo dữ liệu)
        return sMean > 60 && sStd > 35;
    }

    /**
     * Deskew đơn giản: tìm angle bằng minAreaRect trên các điểm chữ (pixel đen),
     * chỉ xoay khi |angle| đủ lớn để tránh làm xấu ảnh.
     */
    private Mat deskewIfNeeded(Mat binaryBlackOnWhite) {
        Mat inverted = new Mat();
        Core.bitwise_not(binaryBlackOnWhite, inverted); // chữ trắng trên nền đen để findNonZero

        Mat points = new Mat();
        Core.findNonZero(inverted, points);
        if (points.empty() || points.rows() < 50) {
            inverted.release();
            points.release();
            return binaryBlackOnWhite;
        }

        Point[] pointArray = new Point[points.rows()];
        for (int i = 0; i < points.rows(); i++) {
            double[] xy = points.get(i, 0);
            // findNonZero trả về Nx1 với 2 phần tử (x, y)
            pointArray[i] = new Point(xy[0], xy[1]);
        }
        MatOfPoint2f pts2f = new MatOfPoint2f();
        pts2f.fromArray(pointArray);
        RotatedRect rect = Imgproc.minAreaRect(pts2f);
        double angle = rect.angle;
        if (rect.size.width < rect.size.height) {
            angle = angle + 90;
        }

        // chỉ deskew nếu nghiêng đáng kể
        if (Math.abs(angle) < 1.2 || Math.abs(angle) > 25) {
            inverted.release();
            points.release();
            pts2f.release();
            return binaryBlackOnWhite;
        }

        Point center = new Point(binaryBlackOnWhite.width() / 2.0, binaryBlackOnWhite.height() / 2.0);
        Mat rotMat = Imgproc.getRotationMatrix2D(center, angle, 1.0);

        Mat rotated = new Mat(binaryBlackOnWhite.size(), CvType.CV_8UC1);
        Imgproc.warpAffine(binaryBlackOnWhite, rotated, rotMat, binaryBlackOnWhite.size(),
                Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE, new Scalar(255));

        inverted.release();
        points.release();
        pts2f.release();
        rotMat.release();

        return rotated;
    }
}
