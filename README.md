# ĐỒ ÁN LẬP TRÌNH MẠNG: XÂY DỰNG SMTP CLIENT, POP3 CLIENT VÀ LOCAL MAIL SERVER

Dự án Java hoàn chỉnh triển khai hai giao thức email chuẩn Internet: **SMTP (Simple Mail Transfer Protocol - RFC 5321)** và **POP3 (Post Office Protocol version 3 - RFC 1939)** bằng **Java TCP Socket** (`Socket`, `ServerSocket`), không sử dụng thư viện bên ngoài (như JavaMail/Jakarta Mail) để thay thế logic cốt lõi của giao thức.

Dự án bao gồm:
1. **SMTP Client**: Soạn thảo và gửi email văn bản UTF-8 qua kết nối TCP Socket.
2. **POP3 Client**: Đăng nhập, liệt kê thư, tải đọc thư và quản lý xóa thư theo trạng thái phiên.
3. **Local Mail Server**: Tích hợp cả **SMTP Server** (nhận thư, lưu file `.eml`) và **POP3 Server** (cho phép client đọc/xóa thư), bảo vệ chống Open-Relay, ngăn chặn Path Traversal và quản lý mật khẩu băm bảo mật.
4. **Server Dashboard & GUI**: Giao diện Java Swing trực quan với 4 tab quản lý toàn diện.

---

## MỤC LỤC
- [1. Mục tiêu và Chức năng](#1-mục-tiêu-và-chức-năng)
- [2. Kiến trúc Hệ thống](#2-kiến-trúc-hệ-thống)
- [3. Sơ đồ Luồng Giao thức](#3-sơ-đồ-luồng-giao-thức)
  - [3.1. Luồng Gửi Thư SMTP](#31-luồng-gửi-thư-smtp)
  - [3.2. Luồng Nhận và Đọc Thư POP3](#32-luồng-nhận-và-đọc-thư-pop3)
- [4. Bảng Giải thích Lệnh và Mã Phản hồi](#4-bảng-giải-thích-lệnh-và-mã-phản-hồi)
  - [4.1. Lệnh và Mã Phản hồi SMTP (RFC 5321)](#41-lệnh-và-mã-phản-hồi-smtp-rfc-5321)
  - [4.2. Lệnh và Mã Phản hồi POP3 (RFC 1939)](#42-lệnh-và-mã-phản-hồi-pop3-rfc-1939)
- [5. Vai trò các Thành phần](#5-vai-trò-các-thành-phần)
- [6. Cấu trúc Thư mục và Lưu trữ](#6-cấu-trúc-thư-mục-và-lưu-trữ)
- [7. Tài khoản Demo và Cơ chế Xác thực](#7-tài-khoản-demo-và-cơ-chế-xác-thực)
- [8. Hướng dẫn Cài đặt, Build và Chạy trên Ubuntu / Linux](#8-hướng-dẫn-cài-đặt-build-và-chạy-trên-ubuntu--linux)
- [9. Hướng dẫn Mở dự án trong IntelliJ IDEA](#9-hướng-dẫn-mở-dự-án-trong-intellij-idea)
- [10. Kịch bản Demo Chi tiết (Từng bước)](#10-kịch-bản-demo-chi-tiết-từng-bước)
- [11. Kiểm thử Tự động (JUnit 5 & Integration Tests)](#11-kiểm-thử-tự-động-junit-5--integration-tests)
- [12. Bảo mật và Cảnh báo](#12-bảo-mật-và-cảnh-báo)
- [13. Giới hạn Hiện tại và Hướng Phát triển](#13-giới-hạn-hiện-tại-và-hướng-phát-triển)

---

## 1. Mục tiêu và Chức năng

- **Tự lập trình TCP Socket**: Sử dụng trực tiếp `java.net.Socket` và `java.net.ServerSocket` với luồng I/O UTF-8 (`BufferedReader`, `BufferedWriter`).
- **Xử lý chuẩn RFC**:
  - Quy ước xuống dòng chuẩn `\r\n` (CRLF).
  - Kỹ thuật **Dot-stuffing** (khi gửi) và **Dot-unstuffing** (khi nhận) để phân biệt nội dung thư với dấu chấm kết thúc `\r\n.\r\n`.
  - Phân tích phản hồi một dòng và nhiều dòng (RFC 5321 multiline reply: `250-...` và `250 OK`).
  - Hỗ trợ mã hóa tiêu đề RFC 2047 (`=?UTF-8?B?...?=`) cho tiếng Việt có dấu.
- **Quản lý phiên POP3 theo 3 trạng thái**:
  - `AUTHORIZATION`: Kiểm tra `USER` và `PASS`, khóa hộp thư (`exclusive mailbox lock`).
  - `TRANSACTION`: Thực thi `STAT`, `LIST`, `UIDL`, `RETR`, `DELE` (đánh dấu xóa), `RSET`.
  - `UPDATE`: Khi nhận lệnh `QUIT`, server thực sự xóa các file thư đã đánh dấu khỏi ổ đĩa. Nếu kết nối đứt đột ngột trước `QUIT`, các thư đánh dấu sẽ **không** bị xóa.
- **Hoạt động độc lập Offline**: Toàn bộ hệ thống chạy trên máy local (`127.0.0.1`), không cần internet, không phụ thuộc vào Gmail hay máy chủ bên ngoài.

---

## 2. Kiến trúc Hệ thống

```
+---------------------------------------------------------------------------------+
|                              JAVA SWING GUI                                     |
|  +--------------------+  +--------------------+  +----------------------------+ |
|  |  SMTP Client Panel |  |  POP3 Client Panel |  |   Server Dashboard Panel   | |
|  +---------+----------+  +---------+----------+  +-------------+--------------+ |
+------------|-----------------------|---------------------------|----------------+
             |                       |                           |
             v                       v                           v
     +---------------+       +---------------+           +---------------+
     |  SmtpClient   |       |  Pop3Client   |           | ProtocolLogger|
     | (Java Socket) |       | (Java Socket) |           +-------+-------+
     +-------+-------+       +-------+-------+                   ^
             |                       |                           |
====== TCP Port 2525 =========== TCP Port 1110 ==================|=================
             |                       |                           |
             v                       v                           |
     +---------------+       +---------------+                   |
     |  SmtpServer   |       |  Pop3Server   |                   |
     | ServerSocket  |       | ServerSocket  |-------------------+
     +-------+-------+       +-------+-------+
             |                       |
             +-----------+-----------+
                         |
         +---------------+---------------+
         |                               |
         v                               v
+------------------+           +-------------------+
|  AccountManager  |           |  FileMailStorage  |
|  (PBKDF2 Hash)   |           |  (RFC 5322 .eml)  |
| accounts.json    |           | data/mailboxes/   |
+------------------+           +-------------------+
```

---

## 3. Sơ đồ Luồng Giao thức

### 3.1. Luồng Gửi Thư SMTP

```
Client (SmtpClient)                          Server (SmtpServer)
     |                                               |
     | ------------ TCP Handshake -----------------> | (ServerSocket.accept)
     | <----------- 220 ESMTP Service ready -------- | (Greeting)
     |                                               |
     | ------------ EHLO localhost ----------------> |
     | <----------- 250-127.0.0.1 ------------------ |
     | <----------- 250-AUTH LOGIN PLAIN ----------- |
     | <----------- 250 OK ------------------------- |
     |                                               |
     | ------------ MAIL FROM:<duc@localhost> -----> | (Kiểm tra sender hợp lệ)
     | <----------- 250 2.1.0 Sender OK ------------ |
     |                                               |
     | ------------ RCPT TO:<lan@localhost> -------> | (Kiểm tra mailbox tồn tại)
     | <----------- 250 2.1.5 Recipient OK --------- | (Nếu sai: 550 User not found)
     |                                               |
     | ------------ DATA --------------------------> |
     | <----------- 354 Start mail input ----------- |
     |                                               |
     | ------------ Header + Body (Dot-stuffing) --> | (Nhận dòng, bỏ chấm dư)
     | ------------ CRLF . CRLF -------------------> | (Nhận dấu kết thúc)
     |                                               | [Lưu file .eml xuống đĩa]
     | <----------- 250 2.0.0 OK: message queued --- | (Chỉ báo OK khi đã lưu bền vững)
     |                                               |
     | ------------ QUIT --------------------------> |
     | <----------- 221 Closing channel ------------ |
     | ------------ Close Socket ------------------> |
```

### 3.2. Luồng Nhận và Đọc Thư POP3

```
Client (Pop3Client)                          Server (Pop3Server)
     |                                               |
     | ------------ TCP Handshake -----------------> |
     | <----------- +OK POP3 server ready ---------- | [Trạng thái AUTHORIZATION]
     |                                               |
     | ------------ USER lan ----------------------> |
     | <----------- +OK User name accepted --------- |
     |                                               |
     | ------------ PASS lan123 -------------------> | (Xác thực PBKDF2 hash)
     | <----------- +OK Mailbox ready -------------- | [Trạng thái TRANSACTION]
     |                                               | (Khóa độc quyền mailbox 'lan')
     |                                               |
     | ------------ STAT --------------------------> |
     | <----------- +OK 1 386 ---------------------- | (1 thư, tổng dung lượng 386 bytes)
     |                                               |
     | ------------ LIST --------------------------> |
     | <----------- +OK 1 messages (386 octets) ---- |
     | <----------- 1 386 -------------------------- |
     | <----------- . ------------------------------ |
     |                                               |
     | ------------ RETR 1 ------------------------> |
     | <----------- +OK 386 octets ----------------- |
     | <----------- [Toàn bộ nội dung .eml] -------- | (Kèm Dot-stuffing nếu có dòng bắt đầu bằng '.')
     | <----------- . ------------------------------ | (Dấu kết thúc khối)
     |                                               |
     | ------------ DELE 1 ------------------------> | (Đánh dấu xóa trong phiên)
     | <----------- +OK message 1 marked deleted --- | (Chưa xóa vật lý trên ổ đĩa)
     |                                               |
     | ------------ QUIT --------------------------> | [Trạng thái UPDATE]
     |                                               | (Xóa vĩnh viễn file .eml đã đánh dấu)
     |                                               | (Mở khóa mailbox)
     | <----------- +OK signing off (1 deleted) ---- |
     | ------------ Close Socket ------------------> |
```

---

## 4. Bảng Giải thích Lệnh và Mã Phản hồi

### 4.1. Lệnh và Mã Phản hồi SMTP (RFC 5321)

| Lệnh SMTP | Ý nghĩa | Ví dụ |
| :--- | :--- | :--- |
| `EHLO` | Chào máy chủ (Extended SMTP), hỏi danh sách tính năng hỗ trợ | `EHLO localhost` |
| `HELO` | Chào máy chủ (chuẩn cũ RFC 821) khi EHLO không khả dụng | `HELO localhost` |
| `AUTH` | Xác thực người dùng (hỗ trợ `LOGIN` và `PLAIN` với Base64) | `AUTH LOGIN` |
| `MAIL FROM`| Khởi tạo giao dịch gửi thư, chỉ định địa chỉ người gửi | `MAIL FROM:<duc@localhost>` |
| `RCPT TO` | Chỉ định một người nhận thư (có thể gửi nhiều lệnh) | `RCPT TO:<lan@localhost>` |
| `DATA` | Bắt đầu truyền phần tiêu đề và thân thư | `DATA` |
| `.` (dấu chấm)| Dòng chỉ chứa ký tự `.` đứng sau CRLF để kết thúc nội dung DATA | `\r\n.\r\n` |
| `RSET` | Hủy bỏ giao dịch thư hiện tại, xóa phong bì gửi/nhận | `RSET` |
| `NOOP` | Kiểm tra đường truyền (No Operation), giữ kết nối | `NOOP` |
| `QUIT` | Kết thúc phiên làm việc SMTP và yêu cầu đóng kết nối | `QUIT` |

**Các mã phản hồi SMTP chính:**
- `220`: Dịch vụ SMTP sẵn sàng kết nối.
- `235`: Xác thực tài khoản thành công.
- `250`: Thao tác thành công (`250 OK`, `250 Sender OK`, `250 Recipient OK`, `250 message queued`).
- `354`: Sẵn sàng nhận dữ liệu thư, kết thúc bằng `<CRLF>.<CRLF>`.
- `421`: Dịch vụ bị đóng hoặc timeout.
- `451`: Lỗi cục bộ khi xử lý thư (ví dụ: ghi đĩa thất bại).
- `500`: Lỗi cú pháp lệnh không nhận diện được.
- `501`: Lỗi cú pháp tham số hoặc địa chỉ email.
- `503`: Sai trình tự lệnh (ví dụ: gửi `RCPT TO` trước khi gửi `MAIL FROM`).
- `535`: Thông tin đăng nhập không hợp lệ.
- `550`: Hộp thư người nhận không tồn tại (chống chuyển tiếp tự do / Open-Relay).
- `552`: Vượt quá kích thước thư tối đa cho phép.

---

### 4.2. Lệnh và Mã Phản hồi POP3 (RFC 1939)

| Lệnh POP3 | Trạng thái cho phép | Ý nghĩa | Ví dụ |
| :--- | :--- | :--- | :--- |
| `USER` | AUTHORIZATION | Khai báo tên tài khoản người dùng | `USER lan` |
| `PASS` | AUTHORIZATION | Khai báo mật khẩu để xác thực và khóa hộp thư | `PASS lan123` |
| `STAT` | TRANSACTION | Trả về tổng số thư còn hiệu lực và tổng dung lượng byte | `STAT` -> `+OK 2 1530` |
| `LIST` | TRANSACTION | Liệt kê số thứ tự và dung lượng của các thư | `LIST` hoặc `LIST 1` |
| `UIDL` | TRANSACTION | Liệt kê định danh duy nhất (UID) của từng thư | `UIDL` |
| `RETR` | TRANSACTION | Tải toàn bộ nội dung thư (kèm dot-unstuffing) | `RETR 1` |
| `DELE` | TRANSACTION | Đánh dấu thư để xóa khi kết thúc phiên | `DELE 1` |
| `RSET` | TRANSACTION | Hủy bỏ tất cả các đánh dấu xóa thư trong phiên | `RSET` |
| `NOOP` | TRANSACTION | Giữ kết nối hoạt động | `NOOP` |
| `QUIT` | ANY | Đóng phiên làm việc (ở TRANSACTION chuyển sang UPDATE để commit xóa) | `QUIT` |

**Các mã phản hồi POP3:**
- `+OK`: Báo hiệu lệnh thực thi thành công (kèm thông điệp hoặc dữ liệu nhiều dòng).
- `-ERR`: Báo hiệu lệnh thất bại hoặc tham số không hợp lệ (ví dụ: sai mật khẩu, hộp thư đang bị session khác khóa `[IN-USE]`, sai số thứ tự thư).

---

## 5. Vai trò các Thành phần

1. **SMTP Client (`com.mailapp.client.smtp.SmtpClient`)**:
   - Mở socket tới SMTP Server, đàm phán bắt tay `EHLO/HELO`.
   - Xác thực người dùng (nếu bật `SMTP AUTH`).
   - Xây dựng phong bì thư (`MAIL FROM`, `RCPT TO`).
   - Tự động thực hiện **Dot-stuffing** (thêm dấu `.` vào đầu các dòng bắt đầu bằng `.`) để không làm gián đoạn khối dữ liệu kết thúc bằng `\r\n.\r\n`.
   - Kiểm tra mã phản hồi `250 OK` trước khi thông báo gửi thành công.

2. **POP3 Client (`com.mailapp.client.pop3.Pop3Client`)**:
   - Mở socket tới POP3 Server, đăng nhập qua `USER`/`PASS`.
   - Lấy danh sách thư qua `STAT`, `LIST`, `UIDL`.
   - Đọc thư thô qua `RETR`, tự động **Dot-unstuffing** (bỏ dấu `.` phụ).
   - Hỗ trợ đánh dấu xóa (`DELE`) và hủy đánh dấu (`RSET`).
   - Gửi `QUIT` để commit các thao tác xóa và giải phóng kết nối.

3. **Local Mail Server**:
   - **SMTP Server (`SmtpServer`)**: Lắng nghe cổng 2525 (mặc định bind `127.0.0.1`), phân luồng client bằng `ExecutorService`, kiểm tra sự tồn tại của tài khoản nhận thư trong `AccountManager`, lưu thư xuống `MailStorage` thành file `.eml`, chỉ trả lời `250 OK` khi thư đã được lưu bền vững vào đĩa cứng.
   - **POP3 Server (`Pop3Server`)**: Lắng nghe cổng 1110 (mặc định bind `127.0.0.1`), quản lý máy trạng thái RFC 1939. Khóa độc quyền mailbox (`Mailbox Lock`) nhằm ngăn ngừa xung đột khi nhiều phiên cùng truy cập một hộp thư. Chỉ thực hiện xóa vật lý thư trong trạng thái `UPDATE` (khi lệnh `QUIT` được gửi).
   - **Account Manager (`AccountManager`)**: Quản lý tài khoản, mã hóa mật khẩu an toàn bằng thuật toán **PBKDF2WithHmacSHA256** kết hợp chuỗi muối ngẫu nhiên (salt 16-byte) với 10.000 vòng lặp. Lưu trữ trong `data/accounts/accounts.json`.
   - **Mail Storage (`FileMailStorage`)**: Quản lý việc lưu, đọc, liệt kê và xóa các file thư điện tử `.eml`. Ngăn chặn lỗ hổng Path Traversal (`../`) khi thao tác trên đường dẫn.

4. **Server Dashboard & Swing GUI**:
   - Giao diện trực quan tích hợp 4 tab chuyên biệt.
   - Sử dụng đa luồng bất đồng bộ (`SwingWorker`) để các thao tác mạng qua Socket không bao giờ gây treo (freeze) giao diện người dùng.

---

## 6. Cấu trúc Thư mục và Lưu trữ

Cấu trúc dự án và thư mục dữ liệu:

```
MailClient/
├── pom.xml                               # File cấu hình Maven (Java 21, JUnit 5, Jackson)
├── README.md                             # Tài liệu hướng dẫn chi tiết
├── data/                                 # Thư mục lưu trữ dữ liệu cục bộ
│   ├── accounts/
│   │   └── accounts.json                 # Danh sách tài khoản đã băm mật khẩu PBKDF2
│   └── mailboxes/                        # Hộp thư của từng người dùng
│       ├── duc/
│       │   └── 1792387115199_06bc515b.eml
│       └── lan/
│           └── 1792387136269_52608580.eml
└── src/
    ├── main/java/com/mailapp/
    │   ├── Main.java                     # Điểm khởi chạy ứng dụng (GUI / Headless Server / CLI Demo)
    │   ├── account/
    │   │   ├── Account.java              # Model thông tin tài khoản
    │   │   ├── AccountManager.java       # Quản lý tài khoản và lưu trữ file JSON
    │   │   └── PasswordHasher.java       # Băm mật khẩu PBKDF2 với muối ngẫu nhiên
    │   ├── client/
    │   │   ├── pop3/
    │   │   │   ├── Pop3Client.java       # POP3 Client sử dụng TCP Socket
    │   │   │   └── Pop3Response.java     # Model phản hồi POP3 (+OK / -ERR)
    │   │   └── smtp/
    │   │       ├── SmtpClient.java       # SMTP Client sử dụng TCP Socket
    │   │       └── SmtpResponse.java     # Model phản hồi SMTP (Mã số & Thông điệp)
    │   ├── mime/
    │   │   ├── MimeHeaderEncoder.java   # Mã hóa/giải mã tiêu đề RFC 2047 (tiếng Việt có dấu)
    │   │   ├── MimeParser.java          # Phân tích file .eml thành đối tượng EmailMessage
    │   │   └── MimeWriter.java          # Xuất email thành định dạng RFC 5322 chuẩn CRLF
    │   ├── model/
    │   │   ├── EmailMessage.java        # Thực thể Email (From, To, Cc, Subject, Body...)
    │   │   ├── MailServerConfig.java    # Cấu hình IP, Port, giới hạn dung lượng, Timeout
    │   │   └── Pop3MessageInfo.java     # Metadata thư trong phiên POP3
    │   ├── server/
    │   │   ├── pop3/
    │   │   │   ├── Pop3Server.java       # POP3 ServerSocket lắng nghe kết nối
    │   │   │   ├── Pop3SessionHandler.java # Máy trạng thái xử lý từng kết nối POP3
    │   │   │   └── Pop3State.java        # Enum trạng thái POP3 (AUTHORIZATION, TRANSACTION, UPDATE)
    │   │   └── smtp/
    │   │       ├── SmtpServer.java       # SMTP ServerSocket lắng nghe kết nối
    │   │       └── SmtpSessionHandler.java # Xử lý giao thức SMTP cho từng client
    │   ├── storage/
    │   │   ├── FileMailStorage.java     # Triển khai lưu trữ file .eml trên đĩa
    │   │   └── MailStorage.java         # Interface lưu trữ thư
    │   ├── ui/
    │   │   ├── LogsPanel.java           # Tab hiển thị Protocol Log toàn hệ thống
    │   │   ├── MainFrame.java           # Khung cửa sổ chính chứa các tab
    │   │   ├── Pop3ClientPanel.java     # Tab giao diện POP3 Client
    │   │   ├── ServerDashboardPanel.java # Tab điều khiển và giám sát Server
    │   │   └── SmtpClientPanel.java     # Tab giao diện SMTP Client
    │   └── util/
    │       ├── EmailValidator.java      # Kiểm tra địa chỉ, CRLF injection, Path Traversal
    │       └── ProtocolLogger.java      # Ghi log giao thức, che mật khẩu nhạy cảm
    └── test/java/com/mailapp/           # Bộ kiểm thử tự động JUnit 5
        ├── DotStuffingTest.java
        ├── EndToEndMailFlowIntegrationTest.java
        ├── account/
        │   ├── AccountManagerTest.java
        │   └── PasswordHasherTest.java
        ├── client/
        │   ├── pop3/Pop3ResponseTest.java
        │   └── smtp/SmtpResponseTest.java
        ├── mime/MimeParserAndWriterTest.java
        ├── server/
        │   ├── pop3/Pop3ServerIntegrationTest.java
        │   └── smtp/SmtpServerIntegrationTest.java
        ├── storage/FileMailStorageTest.java
        └── util/EmailValidatorTest.java
```

---

## 7. Tài khoản Demo và Cơ chế Xác thực

Khi khởi chạy lần đầu tiên, nếu tệp `data/accounts/accounts.json` chưa tồn tại, hệ thống sẽ tự động khởi tạo 2 tài khoản demo mặc định:

| Tên đăng nhập (Username) | Mật khẩu (Password) | Địa chỉ Email tương ứng | Mục đích |
| :--- | :--- | :--- | :--- |
| `duc` | `duc123` | `duc@localhost` | Tài khoản gửi thư demo |
| `lan` | `lan123` | `lan@localhost` | Tài khoản nhận thư demo |

### Cơ chế băm mật khẩu:
- Mật khẩu **không bao giờ** được lưu dưới dạng văn bản rõ (plaintext).
- Mỗi tài khoản được cấp một chuỗi ngẫu nhiên (Salt) 16 bytes bằng `SecureRandom`.
- Thuật toán băm: `PBKDF2WithHmacSHA256` với 10.000 vòng lặp (iterations).
- So sánh mật khẩu bằng phép so khớp hằng số thời gian `MessageDigest.isEqual` để ngăn chặn tấn công Timing Attack.
- Server khởi động lại sẽ đọc từ file JSON hiện có và **không ghi đè** tài khoản người dùng đã tạo.

---

## 8. Hướng dẫn Cài đặt, Build và Chạy trên Ubuntu / Linux

### 8.1. Yêu cầu hệ thống
- Hệ điều hành: Ubuntu 20.04 / 22.04 / 24.04 / Linux tương đương.
- Java Development Kit: OpenJDK 21 trở lên (`java -version`).
- Apache Maven: Phiên bản 3.8+ (`mvn -version`).

Nếu máy chưa cài đặt:
```bash
sudo apt update
sudo apt install -y openjdk-21-jdk maven
```

### 8.2. Build dự án và chạy Unit Tests
Tại thư mục gốc của dự án (`/home/hduc/IdeaProjects/MailClient`):
```bash
mvn clean test
```
*Kết quả mong đợi:* Tất cả 35 kiểm thử Unit Test và Integration Test đều vượt qua (`BUILD SUCCESS`).

### 8.3. Đóng gói thành file Executable JAR
```bash
mvn package
```
Lệnh này sẽ tạo ra file `target/mail-client-server-1.0.0.jar` chứa đầy đủ phụ thuộc (Fat JAR).

### 8.4. Các chế độ chạy ứng dụng

#### 1. Khởi chạy Giao diện đồ họa Swing (Mặc định)
```bash
java -jar target/mail-client-server-1.0.0.jar
```
*(Cửa sổ đồ họa sẽ xuất hiện với 4 tab: SMTP Client, POP3 Client, Server Dashboard và Protocol Logs)*

#### 2. Khởi chạy kịch bản tự động kiểm thử toàn diện (CLI Demo)
Dành cho môi trường Terminal không có màn hình hiển thị (Headless):
```bash
java -jar target/mail-client-server-1.0.0.jar --demo
```
*(Chương trình sẽ tự động khởi động server trên cổng 2525 và 1110, gửi thư từ `duc` sang `lan` qua Socket, kiểm tra và đọc thư qua POP3, sau đó dọn dẹp an toàn)*

#### 3. Khởi chạy riêng Local Mail Server nền (Headless Server)
```bash
java -jar target/mail-client-server-1.0.0.jar --server
```
*(Server sẽ duy trì hoạt động trên cổng 2525 và 1110 cho đến khi bạn bấm `Ctrl+C`)*

---

## 9. Hướng dẫn Mở dự án trong IntelliJ IDEA

1. Mở IntelliJ IDEA.
2. Chọn **File** -> **Open...**
3. Trỏ tới thư mục `MailClient` (`/home/hduc/IdeaProjects/MailClient`) và nhấn **OK**.
4. Chọn **Open as Project**.
5. IntelliJ sẽ nhận diện file `pom.xml` và tự động tải thư viện cần thiết.
6. Thiết lập Project SDK:
   - Vào **File** -> **Project Structure** -> **Project**.
   - Mục **SDK**: Chọn Java 21 (JDK 21).
   - Mục **Language level**: Chọn `21 - Pattern matching, record patterns...`.
7. Chạy ứng dụng:
   - Mở file `src/main/java/com/mailapp/Main.java`.
   - Nhấn chuột phải và chọn **Run 'Main.main()'**.

---

## 10. Kịch bản Demo Chi tiết (Từng bước)

Dưới đây là kịch bản chuẩn để trình diễn đồ án trước giảng viên:

### Bước 1: Khởi động Local Mail Server
1. Chạy ứng dụng đồ họa: `java -jar target/mail-client-server-1.0.0.jar`.
2. Ứng dụng tự động mở vào tab **Server Dashboard**.
3. Nhấn nút **Start SMTP** (Cổng mặc định: 2525). Trạng thái chuyển sang huy hiệu màu xanh: `RUNNING :2525`.
4. Nhấn nút **Start POP3** (Cổng mặc định: 1110). Trạng thái chuyển sang huy hiệu màu xanh: `RUNNING :1110`.
5. Quan sát ô log máy chủ bên dưới xác nhận 2 dịch vụ đã sẵn sàng trên `127.0.0.1`.

### Bước 2: Soạn và Gửi Email bằng SMTP Client
1. Chuyển sang tab **SMTP Client**.
2. Kiểm tra thông tin kết nối:
   - Host: `127.0.0.1` | Port: `2525`.
   - Tùy chọn `SMTP AUTH`: Bỏ chọn (hoặc chọn nếu muốn thử xác thực).
3. Nhấn **Test Connection** để kiểm tra bắt tay `220` và lệnh `EHLO`.
4. Nhập thông tin thư:
   - **From**: `duc@localhost`
   - **To**: `lan@localhost`
   - **Subject**: `Kiểm thử đồ án Lập trình mạng Socket`
   - **Body**:
     ```
     Chào Lan,
     Email này được gửi trực tiếp qua Java TCP Socket.
     .Dòng này bắt đầu bằng dấu chấm để kiểm tra kỹ thuật Dot-stuffing.
     Chúc bạn một ngày làm việc hiệu quả!
     ```
5. Nhấn nút **Send Email**.
6. Hộp thoại thông báo xuất hiện: `Gửi thư thành công! Phản hồi máy chủ: 250 2.0.0 OK: message queued ...`.
7. Khung log bên dưới ghi nhận toàn bộ chuỗi lệnh: `EHLO` -> `MAIL FROM` -> `RCPT TO` -> `DATA` -> `.` -> `250 OK` -> `QUIT`.

### Bước 3: Kiểm tra Email được Lưu trên Đĩa
1. Chuyển về tab **Server Dashboard**.
2. Chọn hộp thư `lan` từ danh sách `Select Mailbox`.
3. Bảng hiển thị danh sách thư trong hộp thư `lan` sẽ hiển thị file `.eml` vừa được lưu với tiêu đề, người gửi, ngày gửi và dung lượng byte chính xác.
4. Nhấn **Open Storage Folder** để mở thư mục trên hệ điều hành và kiểm tra file `.eml` thực tế.

### Bước 4: Nhận và Đọc Email bằng POP3 Client
1. Chuyển sang tab **POP3 Client**.
2. Điền thông tin kết nối:
   - Host: `127.0.0.1` | Port: `1110`
   - User: `lan` | Pass: `lan123`
3. Nhấn nút **Connect & Login**.
4. Log giao thức hiển thị quá trình: gửi `USER lan` -> `+OK`, gửi `PASS ********` -> `+OK Mailbox ready`.
5. Danh sách thư hiển thị thư vừa gửi.
6. Chọn thư trong bảng và nhấn **Read Email** (hoặc nhấp đúp vào dòng):
   - Tiêu đề Unicode tiếng Việt được giải mã hoàn hảo.
   - Thân thư hiển thị đầy đủ, dòng `.Dòng này bắt đầu bằng dấu chấm` được Dot-unstuffing hiển thị nguyên vẹn.

### Bước 5: Kiểm tra Cơ chế Xóa Thư và Trạng thái Phiên POP3
1. Chọn email trong bảng và nhấn **Delete**.
2. Hộp thoại cảnh báo xuất hiện giải thích quy tắc chuẩn POP3: *Lệnh DELE chỉ đánh dấu thư để xóa trong phiên hiện tại. Thư chỉ bị xóa thực sự khi thoát phiên bằng QUIT*. Nhấn **Yes**.
3. Cột Status của thư chuyển sang `MARKED DELETED`.
4. Nhấn nút **Reset (RSET)**: Cột Status quay trở lại trạng thái bình thường `OK` (chứng minh lệnh `RSET` đã hủy các đánh dấu xóa).
5. Nhấn **Delete** lại một lần nữa để đánh dấu xóa.
6. Nhấn nút **Disconnect (QUIT)**: Client gửi lệnh `QUIT`, server chuyển sang trạng thái `UPDATE` và tiến hành xóa file vật lý trên đĩa.
7. Đăng nhập lại vào tài khoản `lan` hoặc kiểm tra Dashboard: Thư đã được xóa sạch hoàn toàn!

---

## 11. Kiểm thử Tự động (JUnit 5 & Integration Tests)

Dự án trang bị bộ kiểm thử gồm **35 automated test cases**:

1. **`SmtpResponseTest`**: Kiểm thử phân tích phản hồi một dòng (`250 OK`), nhiều dòng (`250-...`), mã lỗi tạm thời `4xx` và mã lỗi vĩnh viễn `5xx`.
2. **`Pop3ResponseTest`**: Kiểm thử phân tích phản hồi `+OK`, `-ERR` và danh sách dữ liệu nhiều dòng kết thúc bằng dấu chấm `.`.
3. **`DotStuffingTest`**: Kiểm thử thuật toán chèn thêm dấu chấm (Dot-stuffing) khi gửi và bóc tách dấu chấm (Dot-unstuffing) khi nhận, đảm bảo không nhầm lẫn với dấu chấm kết thúc `\r\n.\r\n`.
4. **`MimeParserAndWriterTest`**: Kiểm thử đóng gói và phân tích định dạng `.eml`, mã hóa và giải mã tiêu đề tiếng Việt RFC 2047 (`=?UTF-8?B?...?=`), phân tích tiêu đề gấp dòng (folded headers), và nhận diện cấu trúc multipart.
5. **`EmailValidatorTest`**: Kiểm thử phát hiện và ngăn chặn tấn công chèn dòng CRLF Injection (`\r\n`) trong các trường header; kiểm thử ngăn chặn tấn công đường dẫn Path Traversal (`../`).
6. **`PasswordHasherTest`**: Kiểm thử thuật toán băm mật khẩu PBKDF2WithHmacSHA256, kiểm tra tính ngẫu nhiên của muối và tính đúng đắn khi xác minh mật khẩu.
7. **`AccountManagerTest`**: Kiểm thử khởi tạo tài khoản demo, xác thực mật khẩu, tạo tài khoản mới và đảm bảo không ghi đè file tài khoản hiện có khi khởi động lại.
8. **`FileMailStorageTest`**: Kiểm thử lưu file `.eml`, sinh mã định danh không trùng lặp, đọc dữ liệu thô, xóa thư, cơ chế khóa hộp thư (Mailbox Lock) và độ bền dữ liệu sau khi khởi tạo lại bộ nhớ.
9. **`SmtpServerIntegrationTest`**: Khởi chạy SMTP Server trên cổng ngẫu nhiên (Port 0), kết nối bằng `SmtpClient`, kiểm tra chuỗi lệnh `EHLO`, từ chối người nhận không tồn tại (`550 User not found`), gửi thư thành công và kiểm tra đa luồng đồng thời 5 kết nối cùng lúc.
10. **`Pop3ServerIntegrationTest`**: Khởi chạy POP3 Server trên cổng ngẫu nhiên (Port 0), kiểm thử chuỗi lệnh `USER`, `PASS`, `STAT`, `LIST`, `UIDL`, `RETR`, `DELE`, `RSET`, `QUIT`. Kiểm tra kịch bản ngắt kết nối đột ngột (không gọi `QUIT`) thì thư không bị xóa; kiểm tra khóa độc quyền ngăn phiên thứ hai đăng nhập vào cùng một hộp thư.
11. **`EndToEndMailFlowIntegrationTest`**: Kiểm thử tích hợp toàn trình khép kín: khởi động cả 2 server, gửi thư từ `duc` sang `lan`, kiểm tra tệp `.eml`, dùng POP3 đọc thư, đánh dấu xóa, xác nhận xóa, khởi động lại server và kiểm tra thư còn lại vẫn bền vững.

Lệnh chạy kiểm thử:
```bash
mvn clean test
```

---

## 12. Bảo mật và Cảnh báo

1. **Phạm vi sử dụng**: Dự án được thiết kế cho mục đích **học tập, nghiên cứu và thực hành môn Lập trình Mạng**. Không sử dụng trực tiếp làm Mail Server công cộng trên Internet mà chưa có các lớp bảo vệ chuyên sâu.
2. **Cơ chế bind an toàn**: Mặc định Server chỉ mở socket lắng nghe trên địa chỉ Loopback `127.0.0.1`, không tự ý bind ra `0.0.0.0` để tránh rủi ro bảo mật trên mạng LAN/WiFi công cộng.
3. **Chống Open-Relay**: SMTP Server chỉ chấp nhận nhận thư cho các tài khoản người dùng nội bộ đã được đăng ký trong hệ thống (`AccountManager`). Bất kỳ địa chỉ người nhận nào không tồn tại đều bị từ chối bằng mã lỗi `550 User not found / Mailbox unavailable`.
4. **Ngăn chặn Path Traversal**: Tên người dùng và tên file được lọc và chuẩn hóa nghiêm ngặt bằng biểu thức chính quy `^[a-zA-Z0-9._-]+$`. Hệ thống lập tức ném ngoại lệ bảo mật nếu phát hiện ký tự `..`, `/` hoặc `\`.
5. **Che giấu dữ liệu nhạy cảm trong Log**: Thành phần `ProtocolLogger` tự động phát hiện và che giấu các lệnh chứa mật khẩu như `PASS ********` hoặc dữ liệu mã hóa base64 trong `AUTH PLAIN` để tránh lộ thông tin người dùng trong log và trên màn hình giao diện.

---

## 13. Giới hạn Hiện tại và Hướng Phát triển

### Giới hạn hiện tại:
- Chỉ hỗ trợ email dạng văn bản thuần UTF-8 (`text/plain`). Với email multipart hoặc HTML phức tạp, hệ thống sẽ trích xuất phần văn bản hoặc thông báo định dạng thay vì hiển thị lỗi.
- Chưa hỗ trợ lưu trữ và tải tệp đính kèm nhị phân (Binary attachments).
- Mặc định sử dụng kênh truyền socket không mã hóa trong môi trường local demo (chưa tích hợp chứng chỉ SSL/TLS tự ký hoàn chỉnh trên server).

### Hướng phát triển:
- Bổ sung cấu hình Keystore/Truststore để hỗ trợ kết nối bảo mật `STARTTLS` và `SMTPS/POP3S` trên cổng 465/995.
- Bổ sung chức năng đính kèm và tải file (Attachment Base64 MIME multipart).
- Phát triển tính năng tạo và quản lý tài khoản người dùng trực tiếp trên giao diện Dashboard.
- Hỗ trợ lưu trữ thư trên cơ sở dữ liệu (SQLite / PostgreSQL) bên cạnh lưu trữ tệp `.eml`.
