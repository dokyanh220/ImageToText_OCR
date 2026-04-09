import { useState, useRef } from "react";
import type { ChangeEvent, DragEvent } from "react";
import axios from "axios";
import { UploadCloud, FileType2, FileText, Copy, Download, Check, Loader2, Image as ImageIcon, AlertCircle } from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";

export default function App() {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [text, setText] = useState<string>("");
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string>("");
  const [copied, setCopied] = useState<boolean>(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const validateFile = (f: File) => {
    if (f.type !== "image/png" && f.type !== "image/jpeg") {
      setError("Vui lòng chỉ tải lên file định dạng PNG hoặc JPG.");
      return false;
    }
    setError("");
    return true;
  };

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const f = e.target.files[0];
      if (validateFile(f)) {
        setFile(f);
        setPreview(URL.createObjectURL(f));
        setText("");
      } else {
        setFile(null);
        setPreview(null);
      }
    }
  };

  const handleDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      const f = e.dataTransfer.files[0];
      if (validateFile(f)) {
        setFile(f);
        setPreview(URL.createObjectURL(f));
        setText("");
      } else {
        setFile(null);
        setPreview(null);
      }
    }
  };

  const handleDragOver = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
  };

  const handleOcr = async () => {
    if (!file) {
      setError("Vui lòng chọn ảnh trước khi chuyển đổi.");
      return;
    }

    setLoading(true);
    setError("");

    try {
      const formData = new FormData();
      formData.append("file", file);

      const res = await axios.post(`http://localhost:8080/api/ocr`, formData);
      setText(res.data.text || "Không tìm thấy văn bản trong ảnh.");
    } catch (err) {
      console.error(err);
      setError("Đã xảy ra lỗi khi chuyển đổi. Vui lòng kiểm tra lại server.");
    } finally {
      setLoading(false);
    }
  };

  const handleDownload = () => {
    if (!text) return;

    try {
      const blob = new Blob([text], { type: "text/plain" });
      const url = window.URL.createObjectURL(blob);

      const createdAt = new Date().toISOString().replace(/[-:T]/g, "").split(".")[0];

      const a = document.createElement("a");
      a.href = url;
      a.download = `ITT_${createdAt}.txt`;
      a.click();

      // Cleanup
      window.URL.revokeObjectURL(url);
    } catch (err) {
      console.error(err);
      setError("Lỗi khi tải file. Vui lòng thử lại.");
    }
  };

  const handleCopy = () => {
    if (text) {
      navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4 font-sans text-slate-800">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="w-full max-w-4xl bg-white rounded-3xl shadow-xl overflow-hidden border border-slate-100 flex flex-col md:flex-row"
      >
        {/* Left Panel - Upload Area */}
        <div className="w-full md:w-1/2 p-8 border-b md:border-b-0 md:border-r border-slate-100 flex flex-col">
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-slate-900 flex items-center gap-2">
              <FileText className="text-violet-600 w-8 h-8" />
              Image<span className="text-violet-600">To</span>Text
            </h1>
            <p className="text-slate-500 mt-2 text-sm">Chuyển đổi hình ảnh thành văn bản một cách dễ dàng và nhanh chóng. (Hỗ trợ tốt nhất với hình ảnh văn bản trắng đen rõ ràng, ít họa tiết không phải ký tự văn bản)</p>
          </div>

          <div
            className={`flex-1 min-h-[240px] border-2 border-dashed rounded-2xl flex flex-col items-center justify-center p-6 transition-all duration-200 cursor-pointer ${file ? "border-violet-300 bg-violet-50/50" : "border-slate-200 hover:border-violet-400 hover:bg-slate-50"
              }`}
            onClick={() => fileInputRef.current?.click()}
            onDrop={handleDrop}
            onDragOver={handleDragOver}
          >
            <input
              type="file"
              ref={fileInputRef}
              className="hidden"
              accept=".png, .jpg, .jpeg"
              onChange={handleFileChange}
            />

            <AnimatePresence mode="wait">
              {preview ? (
                <motion.div
                  key="preview"
                  initial={{ opacity: 0, scale: 0.9 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.9 }}
                  className="flex flex-col items-center w-full h-full justify-center"
                >
                  <div className="relative w-full max-h-[160px] flex items-center justify-center rounded-lg overflow-hidden shadow-sm mb-4">
                    <img src={preview} alt="preview" className="max-w-full max-h-[160px] object-contain" />
                  </div>
                  <p className="text-sm font-medium text-slate-700 truncate max-w-[200px]">
                    {file?.name}
                  </p>
                  <p className="text-xs text-slate-400 mt-1">Nhấn để thay đổi ảnh khác</p>
                </motion.div>
              ) : (
                <motion.div
                  key="upload"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  className="flex flex-col items-center text-center"
                >
                  <div className="w-16 h-16 bg-violet-100 rounded-full flex items-center justify-center mb-4 text-violet-600">
                    <UploadCloud className="w-8 h-8" />
                  </div>
                  <h3 className="font-semibold text-slate-700 mb-1">Tải ảnh lên hoặc Kéo thả</h3>
                  <p className="text-sm text-slate-500">Hỗ trợ định dạng PNG, JPG</p>
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          <AnimatePresence>
            {error && (
              <motion.div
                initial={{ opacity: 0, height: 0, marginTop: 0 }}
                animate={{ opacity: 1, height: 'auto', marginTop: 16 }}
                exit={{ opacity: 0, height: 0, marginTop: 0 }}
                className="overflow-hidden"
              >
                <div className="text-red-500 flex items-center gap-2 text-sm bg-red-50 p-3 rounded-lg">
                  <AlertCircle className="w-4 h-4 flex-shrink-0" />
                  <p>{error}</p>
                </div>
              </motion.div>
            )}
          </AnimatePresence>


          <button
            onClick={handleOcr}
            disabled={!file || loading}
            className={`mt-6 w-full py-4 rounded-xl font-medium text-white shadow-md transition-all flex items-center justify-center gap-2 ${!file
                ? "bg-slate-300 cursor-not-allowed shadow-none"
                : "bg-violet-600 hover:bg-violet-700 hover:shadow-lg active:scale-[0.98]"
              }`}
          >
            {loading ? (
              <>
                <Loader2 className="w-5 h-5 animate-spin" />
                Đang xử lý...
              </>
            ) : (
              <>
                <FileType2 className="w-5 h-5" />
                Chuyển đổi văn bản
              </>
            )}
          </button>
        </div>

        {/* Right Panel - Result Area */}
        <div className="w-full md:w-1/2 bg-slate-50 p-8 flex flex-col min-h-[400px]">
          {text ? (
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              className="w-full h-full flex flex-col"
            >
              <div className="flex items-center justify-between mb-4">
                <h3 className="font-semibold text-slate-700 flex items-center gap-2">
                  <Check className="w-5 h-5 text-emerald-500" />
                  Kết quả
                </h3>
              </div>

              <div className="relative flex-1 bg-white rounded-xl shadow-sm border border-slate-200 p-4 mb-6 group flex flex-col min-h-[200px]">
                <textarea
                  className="flex-1 w-full resize-none outline-none text-slate-600 custom-scrollbar bg-transparent pt-8"
                  value={text}
                  onChange={(e) => setText(e.target.value)}
                />

                <button
                  onClick={handleCopy}
                  className="absolute top-3 right-3 p-2 bg-slate-50 hover:bg-slate-100 text-slate-600 rounded-lg transition-colors flex items-center gap-2 shadow-sm border border-slate-100"
                  title="Sao chép văn bản"
                >
                  {copied ? (
                    <>
                      <Check className="w-4 h-4 text-emerald-600" />
                      <span className="text-xs font-medium text-emerald-600">Đã chép</span>
                    </>
                  ) : (
                    <>
                      <Copy className="w-4 h-4" />
                      <span className="text-xs font-medium">Sao chép</span>
                    </>
                  )}
                </button>
              </div>

              <div className="mt-auto">
                <button
                  onClick={handleDownload}
                  className="w-full py-3 rounded-xl font-medium text-slate-700 bg-white border border-slate-200 hover:bg-slate-50 shadow-sm transition-all flex items-center justify-center gap-2 active:scale-[0.98]"
                >
                  <Download className="w-5 h-5" />
                  Tải file .txt
                </button>
              </div>
            </motion.div>
          ) : (
            <div className="flex flex-col items-center justify-center text-slate-400 h-full w-full py-12">
              <div className="w-24 h-24 mb-6 rounded-full bg-slate-100 flex items-center justify-center shadow-inner">
                <ImageIcon className="w-10 h-10 text-slate-300" />
              </div>
              <p className="text-center font-medium text-slate-500">Kết quả sẽ hiển thị ở đây</p>
              <p className="text-center text-sm mt-2 max-w-xs leading-relaxed">
                Sau khi tải ảnh lên và nhấn chuyển đổi, nội dung văn bản sẽ xuất hiện tại khu vực này.
              </p>
            </div>
          )}
        </div>
      </motion.div>
    </div>
  );
}