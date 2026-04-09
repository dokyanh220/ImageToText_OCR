import { useState, useRef } from "react";
import type { ChangeEvent, DragEvent } from "react";
import axios from "axios";
import {
  UploadCloud,
  FileType2,
  FileText,
  Copy,
  Download,
  Check,
  Loader2,
  Image as ImageIcon,
  AlertCircle,
  FileDigit,
  Sparkles,
  Trash2
} from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";

export default function App() {
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [text, setText] = useState<string>("");
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string>("");
  const [copied, setCopied] = useState<boolean>(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const isPdf = file?.type === "application/pdf" || file?.name.toLowerCase().endsWith(".pdf");

  const validateFile = (f: File) => {
    const validTypes = ["image/png", "image/jpeg", "application/pdf"];
    if (!validTypes.includes(f.type) && !f.name.toLowerCase().endsWith(".pdf")) {
      setError("Vui lòng chỉ tải lên file PNG, JPG hoặc PDF.");
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
        if (f.type.startsWith("image/")) {
          setPreview(URL.createObjectURL(f));
        } else {
          setPreview(null);
        }
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
        if (f.type.startsWith("image/")) {
          setPreview(URL.createObjectURL(f));
        } else {
          setPreview(null);
        }
        setText("");
      }
    }
  };

  const handleOcr = async () => {
    if (!file) return;

    setLoading(true);
    setError("");

    try {
      const formData = new FormData();
      formData.append("file", file);
      formData.append("mode", "ACCURATE");

      const res = await axios.post(`http://localhost:8080/api/ocr`, formData);
      setText(res.data.text || "Không tìm thấy văn bản.");
    } catch (err) {
      console.error(err);
      setError("Đã xảy ra lỗi khi chuyển đổi. Vui lòng kiểm tra lại server.");
    } finally {
      setLoading(false);
    }
  };

  const handleDownloadTxt = () => {
    if (!text) return;
    const blob = new Blob([text], { type: "text/plain" });
    const url = window.URL.createObjectURL(blob);
    const createdAt = new Date().toISOString().replace(/[-:T]/g, "").split(".")[0];
    const a = document.createElement("a");
    a.href = url;
    a.download = `ITT_${createdAt}.txt`;
    a.click();
    window.URL.revokeObjectURL(url);
  };

  const handleDownloadPdf = async () => {
    if (!text || !file) return;

    setLoading(true);
    try {
      const formData = new FormData();
      formData.append("file", file);
      formData.append("mode", "ACCURATE");

      const response = await axios.post(`http://localhost:8080/api/ocr/download/pdf`, formData, {
        responseType: 'blob'
      });

      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      const createdAt = new Date().toISOString().replace(/[-:T]/g, "").split(".")[0];
      link.setAttribute('download', `ITT_${createdAt}.pdf`);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (err) {
      console.error(err);
      setError("Lỗi khi tải PDF.");
    } finally {
      setLoading(false);
    }
  };

  const handleCopy = () => {
    if (text) {
      navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const clearFile = () => {
    setFile(null);
    setPreview(null);
    setText("");
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-6 sm:p-12 relative overflow-hidden">
      {/* Animated background elements */}
      <div className="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] bg-violet-600/20 rounded-full blur-[120px] animate-pulse" />
      <div className="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] bg-blue-600/20 rounded-full blur-[120px] animate-pulse" style={{ animationDelay: '2s' }} />

      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        className="w-full max-w-6xl glass-panel rounded-[2.5rem] overflow-hidden flex flex-col lg:flex-row z-10"
      >
        {/* Left Side: Input */}
        <div className="w-full lg:w-[45%] p-8 sm:p-10 flex flex-col border-b lg:border-b-0 lg:border-r border-white/10">
          <div className="mb-10">
            <motion.div
              initial={{ x: -20, opacity: 0 }}
              animate={{ x: 0, opacity: 1 }}
              className="flex items-center gap-3 mb-3"
            >
              <div className="p-2 premium-gradient rounded-xl shadow-lg shadow-violet-500/30">
                <Sparkles className="text-white w-6 h-6" />
              </div>
              <h1 className="text-3xl font-bold tracking-tight text-white">
                Image<span className="text-violet-400">To</span>Text
              </h1>
            </motion.div>
            <p className="text-slate-500 mt-2 text-sm">Chuyển đổi hình ảnh thành văn bản một cách dễ dàng và nhanh chóng. (Hỗ trợ tốt nhất với hình ảnh văn bản trắng đen rõ ràng, ít họa tiết không phải ký tự văn bản)</p>
          </div>

          <div
            className={`relative flex-1 min-h-[300px] border-2 border-dashed rounded-[2rem] flex flex-col items-center justify-center p-8 transition-all duration-300 group cursor-pointer ${file
              ? "border-violet-500/50 bg-violet-500/5"
              : "border-slate-700 hover:border-violet-500/50 hover:bg-white/5"
              }`}
            onClick={() => !file && fileInputRef.current?.click()}
            onDrop={handleDrop}
            onDragOver={(e) => e.preventDefault()}
          >
            <input
              type="file"
              ref={fileInputRef}
              className="hidden"
              accept=".png, .jpg, .jpeg, .pdf"
              onChange={handleFileChange}
            />

            <AnimatePresence mode="wait">
              {file ? (
                <motion.div
                  key="has-file"
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -10 }}
                  className="flex flex-col items-center w-full h-full text-center"
                >
                  <div className="relative mb-6">
                    {preview ? (
                      <div className="w-48 h-48 rounded-2xl overflow-hidden border border-white/10 shadow-2xl">
                        <img src={preview} alt="preview" className="w-full h-full object-cover" />
                      </div>
                    ) : (
                      <div className="w-48 h-48 rounded-2xl bg-white/5 flex items-center justify-center border border-white/10 shadow-2xl">
                        <FileDigit className="w-16 h-16 text-violet-400" />
                      </div>
                    )}
                    <button
                      onClick={(e) => { e.stopPropagation(); clearFile(); }}
                      className="absolute -top-3 -right-3 p-2 bg-red-500 hover:bg-red-600 text-white rounded-full shadow-lg transition-colors"
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                  <h3 className="text-lg font-semibold text-white mb-1 line-clamp-1 px-4">{file.name}</h3>
                  <p className="text-xs text-slate-500 uppercase tracking-widest">
                    {(file.size / 1024).toFixed(1)} KB • {isPdf ? "Document" : "Image"}
                  </p>
                </motion.div>
              ) : (
                <motion.div
                  key="no-file"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  className="flex flex-col items-center text-center"
                >
                  <div className="w-20 h-20 bg-white/5 rounded-3xl flex items-center justify-center mb-6 group-hover:scale-110 transition-transform duration-300">
                    <UploadCloud className="w-10 h-10 text-violet-400" />
                  </div>
                  <h3 className="text-xl font-semibold text-white mb-2">Tải tệp lên</h3>
                  <p className="text-sm text-slate-400 max-w-[200px]">
                    Kéo thả hoặc nhấn để chọn ảnh (PNG, JPG) hoặc PDF
                  </p>
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          <AnimatePresence>
            {error && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
                className="mt-6 p-4 bg-red-500/10 border border-red-500/20 rounded-2xl flex items-center gap-3 text-red-400 text-sm"
              >
                <AlertCircle size={18} className="shrink-0" />
                <p>{error}</p>
              </motion.div>
            )}
          </AnimatePresence>

          <button
            onClick={handleOcr}
            disabled={!file || loading}
            className={`mt-8 w-full py-4 rounded-2xl font-bold text-white transition-all transform active:scale-[0.98] flex items-center justify-center gap-3 ${!file || loading
              ? "bg-slate-800 text-slate-500 cursor-not-allowed"
              : "premium-gradient shadow-lg shadow-violet-500/20 hover:shadow-violet-500/40 hover:-translate-y-0.5"
              }`}
          >
            {loading ? (
              <Loader2 className="w-6 h-6 animate-spin" />
            ) : (
              <FileType2 className="w-6 h-6" />
            )}
            {loading ? "Đang xử lý..." : "Bắt đầu trích xuất"}
          </button>
        </div>

        {/* Right Side: Output */}
        <div className="w-full lg:w-[55%] p-8 sm:p-10 flex flex-col bg-white/[0.02]">
          <div className="flex items-center justify-between mb-8">
            <h3 className="text-xl font-bold text-white flex items-center gap-3">
              <div className="w-8 h-8 rounded-lg bg-emerald-500/20 flex items-center justify-center">
                <Check className="w-5 h-5 text-emerald-400" />
              </div>
              Kết quả trích xuất
            </h3>
            {text && (
              <button
                onClick={handleCopy}
                className="p-2.5 bg-white/5 hover:bg-white/10 text-slate-300 rounded-xl transition-all border border-white/5 flex items-center gap-2"
              >
                {copied ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                <span className="text-xs font-semibold">{copied ? "Đã chép" : "Sao chép"}</span>
              </button>
            )}
          </div>

          <div className="flex-1 relative mb-8 group min-h-[300px] lg:min-h-0">
            <textarea
              className="w-full h-full min-h-[300px] p-6 bg-white/[0.03] border border-white/10 rounded-[2rem] text-slate-300 focus:outline-none focus:border-violet-500/50 transition-colors custom-scrollbar resize-none leading-relaxed"
              placeholder="Văn bản trích xuất sẽ xuất hiện tại đây..."
              value={text}
              onChange={(e) => setText(e.target.value)}
            />
            {!text && !loading && (
              <div className="absolute inset-0 flex flex-col items-center justify-center text-slate-600 pointer-events-none">
                <ImageIcon size={48} className="mb-4 opacity-20" />
                <p className="text-center text-sm px-8">Chưa có dữ liệu xử lý</p>
              </div>
            )}
            {loading && (
              <div className="absolute inset-0 flex flex-col items-center justify-center bg-[#0f172a]/40 backdrop-blur-[2px] rounded-[2rem]">
                <Loader2 className="w-10 h-10 animate-spin text-violet-400" />
                <p className="mt-4 text-violet-400 font-medium animate-pulse">Đang quét văn bản...</p>
              </div>
            )}
          </div>

          <div className="grid grid-cols-2 gap-4">
            <button
              onClick={handleDownloadTxt}
              disabled={!text}
              className="py-4 rounded-2xl font-bold bg-white/5 border border-white/10 text-slate-300 hover:bg-white/10 transition-all disabled:opacity-30 disabled:cursor-not-allowed flex items-center justify-center gap-2"
            >
              <Download size={20} />
              Tải .TXT
            </button>
            <button
              onClick={handleDownloadPdf}
              disabled={!text || loading}
              className="py-4 rounded-2xl font-bold premium-gradient text-white shadow-lg shadow-violet-500/10 hover:shadow-violet-500/20 transition-all disabled:opacity-30 disabled:cursor-not-allowed flex items-center justify-center gap-2"
            >
              <FileText size={20} />
              Tải PDF
            </button>
          </div>
        </div>
      </motion.div>

      {/* Footer Text */}
      <p className="absolute bottom-6 text-slate-600 text-xs font-medium tracking-widest uppercase">
        Powered by Advanced OCR Technology • 2026
      </p>
    </div>
  );
}