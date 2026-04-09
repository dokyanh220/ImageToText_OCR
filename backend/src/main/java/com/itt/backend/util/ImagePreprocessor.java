package com.itt.backend.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;
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
        Mat img = Imgcodecs.imread(input.getAbsolutePath());
        if (img.empty()) throw new RuntimeException("Không đọc được ảnh!");

        try {
            // 1. Normalize & Resize (2.0x)
            Mat resized = new Mat();
            Imgproc.resize(img, resized, new Size(), 2.0, 2.0, Imgproc.INTER_CUBIC);

            // 2. Xử lý màu sắc - Ưu tiên kênh V (HSV) cho ảnh banner/màu
            Mat gray = extractOptimalGrayscale(resized);

            // 3. Khử nhiễu cấp độ cao (Accurate pipeline)
            Mat denoised = new Mat();
            Photo.fastNlMeansDenoising(gray, denoised, 3, 7, 21);

            // 4. Polarity normalization (Đảm bảo chữ đen nền trắng)
            normalizePolarity(denoised);

            // 5. Sharpening (Làm sắc nét để giữ dấu tiếng Việt)
            Mat sharpened = sharpen(denoised);

            // 6. Thresholding (Adaptive Gaussian)
            Mat binary = new Mat();
            Imgproc.adaptiveThreshold(sharpened, binary, 255, 
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 21, 7);

            // 7. Text Detection (Simple contours filter)
            // Lọc bớt nhiễu bằng cách chỉ giữ các vùng có khả năng là chữ
            Mat filtered = filterContourNoise(binary);

            List<File> outputs = new ArrayList<>();
            outputs.add(writeTempLikeInput(input, filtered, "processed_unified_"));

            // Cleanup
            resized.release();
            gray.release();
            denoised.release();
            sharpened.release();
            binary.release();
            if (filtered != binary) filtered.release();

            return outputs;
        } finally {
            img.release();
        }
    }

    private Mat extractOptimalGrayscale(Mat bgr) {
        // Nếu ảnh có màu sắc mạnh (banner), dùng kênh V của HSV thường cho kết quả tách chữ tốt hơn
        if (isStrongColorBackground(bgr)) {
            Mat hsv = new Mat();
            Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV);
            List<Mat> channels = new ArrayList<>(3);
            Core.split(hsv, channels);
            Mat v = channels.get(2).clone();
            
            hsv.release();
            for (Mat c : channels) c.release();
            return v;
        } else {
            Mat gray = new Mat();
            Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY);
            return gray;
        }
    }

    private void normalizePolarity(Mat gray) {
        Scalar mean = Core.mean(gray);
        if (mean.val[0] < 120) { // Nền tối
            Core.bitwise_not(gray, gray);
        }
    }

    private Mat sharpen(Mat input) {
        Mat sharpened = new Mat();
        Mat kernel = new Mat(3, 3, CvType.CV_32F);
        kernel.put(0, 0, 0, -1, 0, -1, 5, -1, 0, -1, 0);
        Imgproc.filter2D(input, sharpened, -1, kernel);
        kernel.release();
        return sharpened;
    }

    private Mat filterContourNoise(Mat binary) {
        // Đảo ngược để findContours (chữ trắng nền đen)
        Mat inverted = new Mat();
        Core.bitwise_not(binary, inverted);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(inverted, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        Mat clean = new Mat(binary.size(), binary.type(), new Scalar(255));
        for (MatOfPoint cnt : contours) {
            Rect rect = Imgproc.boundingRect(cnt);
            // Lọc các vùng quá nhỏ (nhiễu)
            if (rect.width > 5 && rect.height > 5) {
                // Vẽ lại vùng chữ lên ảnh sạch
                Mat roi = new Mat(binary, rect);
                roi.copyTo(new Mat(clean, rect));
                roi.release();
            }
        }

        inverted.release();
        hierarchy.release();
        return clean;
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
    // Deskew removal logic simplified or replaced by unified robust auto-osd in Tesseract
}
