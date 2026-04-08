# ImageToText (ITT) - Advanced OCR System

ImageToText (ITT) là một ứng dụng web hiện đại cho phép người dùng chuyển đổi hình ảnh thành văn bản một cách nhanh chóng và chính xác. Dự án tập trung vào việc tối ưu hóa nhận diện tiếng Việt (có dấu) và xử lý các loại hình ảnh phức tạp như banner màu, văn bản bị nghiêng hoặc thiếu sáng.

## 🚀 Tính năng nổi bật

*   **Nhận diện Tiếng Việt chuyên sâu**: Sử dụng Tesseract 5 kết hợp với pipeline tiền xử lý ảnh để đảm bảo các dấu tiếng Việt (huyền, sắc, hỏi, ngã, nặng) không bị mất.
*   **Hai chế độ xử lý**:
    *   **Nhanh (Fast)**: Tối ưu cho tài liệu văn bản chuẩn, tốc độ xử lý tức thì.
    *   **Chính xác (Accurate)**: Sử dụng các thuật toán nâng cao (Deskew, Morphology, Multi-pass OCR) cho các hình ảnh khó, banner quảng cáo hoặc font chữ cách điệu.
*   **Xử lý hình ảnh thông minh (OpenCV)**:
    *   Tự động phát hiện và đảo ngược màu (Polarity detection) cho chữ trắng trên nền tối.
    *   Khử nhiễu Gaussian/Median và cân bằng ánh sáng cục bộ (CLAHE).
    *   Tự động căn chỉnh độ nghiêng (Deskew).
*   **Giao diện hiện đại & Tiện ích**:
    *   Cho phép chỉnh sửa trực tiếp văn bản sau khi OCR.
    *   Tải xuống kết quả dưới dạng file `.txt` ngay tại trình duyệt.
    *   Hiệu ứng mượt mà với Framer Motion và thiết kế Premium với Tailwind CSS.

## 🛠 Công nghệ sử dụng

### Backend (Java)
*   **Framework**: Spring Boot 3.x
*   **OCR Engine**: Tesseract 5 (via Tess4J)
*   **Image Processing**: OpenCV 4.x
*   **Language**: Java 21

### Frontend (TypeScript)
*   **Framework**: React 19
*   **Build Tool**: Vite
*   **Styling**: Tailwind CSS 4
*   **Animations**: Framer Motion
*   **Icons**: Lucide React

## 📦 Hướng dẫn cài đặt

### Yêu cầu hệ thống
*   JDK 21
*   Node.js 18+
*   [Tesseract OCR](https://github.com/UB-Mannheim/tesseract/wiki) (Đã cài đặt trên Windows tại `C:/Program Files/Tesseract-OCR/`)

### Chạy Backend
1. Di chuyển vào thư mục backend: `cd backend`
2. Chạy ứng dụng: `mvn spring-boot:run`
*   API sẽ chạy tại: `http://localhost:8080`

### Chạy Frontend
1. Di chuyển vào thư mục frontend: `cd frontend`
2. Cài đặt dependency: `npm install`
3. Chạy môi trường dev: `npm run dev`
*   Ứng dụng sẽ chạy tại: `http://localhost:5173`

---
*Dự án được phát triển với mục tiêu cung cấp công cụ OCR mã nguồn mở mạnh mẽ và dễ sử dụng cho cộng đồng người dùng Việt Nam.*
