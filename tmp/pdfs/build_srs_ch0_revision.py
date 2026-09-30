from pathlib import Path
from textwrap import wrap

from pypdf import PdfReader, PdfWriter
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas


ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(r"C:\Users\MSI PC\Downloads\SRS_ShareLoop_v10_3.pdf")
CH0_PAGE = ROOT / "tmp" / "pdfs" / "srs_ch0_revised_page.pdf"
OUTPUT = ROOT / "output" / "pdf" / "SRS_ShareLoop_v10_3_chuong_0_da_sua.pdf"


HEADER = "SRS ShareLoop — phiên bản v10"
FOOTER = "Trang 6 / 81"

SECTIONS = [
    (
        "0. Tóm tắt toàn bộ hệ thống",
        None,
    ),
    (
        "Hệ thống giải quyết việc gì",
        "ShareLoop là nền tảng web giúp cộng đồng cho, nhận và trao đổi đồ cũ. Người dùng đăng bài về món đồ không còn dùng; người khác gửi yêu cầu xin hoặc đề nghị đổi bằng một món đồ đã đăng của mình; hai bên thống nhất qua khung chat có kiểm soát rồi tự hẹn giao nhận. Mục tiêu là đưa đồ còn tốt đến đúng người cần và giảm rủi ro khi hai người lạ giao dịch.",
    ),
    (
        "Ai dùng hệ thống",
        "Hai actor kế thừa từ User: Member và Admin. Một Member dùng một tài khoản cho mọi vai: đăng bài thì là người cho, gửi yêu cầu thì là người nhận. Bốn hệ thống bên ngoài: cổng thanh toán VNPay, dịch vụ LLM, dịch vụ email SMTP và dịch vụ lưu trữ ảnh. Hệ thống không còn dùng SMS.",
    ),
    (
        "Hệ thống kiếm tiền bằng cách nào",
        "Người dùng nạp tiền qua cổng thanh toán và nhận Credit theo tỉ lệ 1.000đ = 1 Credit. Từ v10, doanh thu nằm ở bước đăng bài chứ không nằm ở bước giao dịch: mỗi bài đăng, dù Cho hay Trao đổi, mất 5 Credit (5.000đ). Sau khi người dùng bấm đăng/gửi bài, hệ thống validate dữ liệu, kiểm tra đủ tối thiểu 5 Credit, tạo bài thành công, trừ ngay 5 Credit, ghi CreditLedger loại POST_FEE và chuyển Item sang PENDING_REVIEW để Admin duyệt hoặc từ chối sau đó. Admin duyệt bài không trừ thêm phí lần nữa và POST_FEE không dùng Hold Credit. Đẩy bài lên đầu kết quả mất phí, và trợ lý AI tính phí sau 5 lượt miễn phí mỗi ngày. Gửi yêu cầu, chủ bài chọn người, chat, chốt lịch và hoàn tất giao dịch đều miễn phí cho cả hai bên. Sửa bài khi còn bị chặn tự động hoặc khi chưa được duyệt thì miễn phí; sau khi bài được duyệt, người đăng được sửa miễn phí thêm 1 lần, từ lần sau tính phí. Credit đi một chiều: nạp vào rồi tiêu, không rút ra.",
    ),
    (
        "Một giao dịch diễn ra thế nào",
        "Người dùng gửi yêu cầu tới một bài đăng. Chủ bài chọn đúng một người. Trước khi chủ bài chọn người, hệ thống không cho phép hai bên trao đổi thông tin liên hệ để tự giao dịch ngoài hệ thống. Sau khi chủ bài đã chọn người, hệ thống mở khung chat, ghim thẻ sản phẩm của cả hai bên ở đầu khung. Hai bên được nhắn tin, và tin nhắn vẫn đi qua ContentModerationService với pipeline hai tầng: tầng 1 normalize text và phát hiện bằng regex/rule-based; tầng 2 chỉ gọi AiClient/LLM khi tầng 1 không xác định rõ trường hợp mơ hồ, rồi phân loại theo confidence threshold. Khi cả hai bên xác nhận lịch hẹn, hệ thống gửi email và số điện thoại của mỗi bên cho bên còn lại. Sau khi gặp nhau, hai bên cùng xác nhận đã giao, đã nhận; bên nhận trả lời thêm câu hỏi \"món đồ có đúng mô tả không\".",
    ),
    (
        "Hệ thống giữ an toàn bằng cách nào",
        "Bài đăng qua ba tầng kiểm duyệt: (1) chặn cứng tại form bằng quy tắc và danh sách từ khoá cấm (lexicon), kể cả số điện thoại, email, đường dẫn trong nội dung; (2) AI đọc văn bản bài đăng và gắn cờ các dấu hiệu khó bắt bằng quy tắc; (3) Admin duyệt thủ công theo checklist cấu hình được. AI không tự duyệt và không tự từ chối bài nào. Sau giao dịch, người dùng có 7 ngày để khiếu nại; Admin xác minh rồi mới trừ sao uy tín. Hệ thống không bồi thường tiền hàng; chế tài chỉ ở cấp tài khoản.",
    ),
    (
        "AI đóng vai trò gì",
        "Ba vai trò, đều chỉ đọc văn bản, không đọc ảnh và không tự thực hiện hành động: trợ lý tìm đồ bằng câu nói tự nhiên; kiểm duyệt chat tầng 2 cho các tin nhắn mơ hồ; sàng lọc bài đăng tầng 2 để gắn cờ hỗ trợ Admin. Tính năng AI gợi ý ghép đôi đã bị bỏ từ v9.",
    ),
]


def draw_wrapped(c, text, x, y, max_chars, font_name="Arial", size=10, leading=13):
    c.setFont(font_name, size)
    for line in wrap(text, width=max_chars, break_long_words=False, replace_whitespace=False):
        c.drawString(x, y, line)
        y -= leading
    return y


def build_page():
    CH0_PAGE.parent.mkdir(parents=True, exist_ok=True)
    pdfmetrics.registerFont(TTFont("Arial", r"C:\Windows\Fonts\arial.ttf"))
    pdfmetrics.registerFont(TTFont("Arial-Bold", r"C:\Windows\Fonts\arialbd.ttf"))

    c = canvas.Canvas(str(CH0_PAGE), pagesize=A4)
    width, height = A4
    left = 54
    right = 54
    y = height - 38

    c.setFont("Arial", 9)
    c.drawString(left, y, HEADER)
    y -= 28

    for title, body in SECTIONS:
        if title.startswith("0."):
            c.setFont("Arial-Bold", 16)
            c.drawString(left, y, title)
            y -= 28
            continue

        c.setFont("Arial-Bold", 11)
        c.drawString(left, y, title)
        y -= 14
        y = draw_wrapped(c, body, left, y, max_chars=103, size=10, leading=12.5)
        y -= 6

    c.setFont("Arial", 9)
    c.drawCentredString(width / 2, 24, FOOTER)
    c.save()


def merge_pdf():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    source = PdfReader(str(SOURCE))
    revised_page = PdfReader(str(CH0_PAGE)).pages[0]
    writer = PdfWriter()
    for index, page in enumerate(source.pages):
        writer.add_page(revised_page if index == 5 else page)
    with OUTPUT.open("wb") as stream:
        writer.write(stream)


if __name__ == "__main__":
    build_page()
    merge_pdf()
    print(OUTPUT)
