# DotPha Macro (Fabric 1.21.4, client-side)

Mod client-side tự động hóa vòng lặp **đột phá / độ kiếp** dựa theo nội dung chat của server.

⚠️ **Lưu ý quan trọng:** đây là macro tự động gửi lệnh dựa theo chat. Nhiều server coi
đây là hành vi macro/auto và có thể **cấm** trong luật chơi — bạn tự chịu trách nhiệm
kiểm tra luật server trước khi dùng.

## Cách hoạt động

1. Nhấn phím `]` (mặc định, đổi được trong `Controls`) → macro **BẬT (armed)**.
2. Cầm item cần dùng ở **hotbar slot 1**, sau đó **click chuột trái** một lần
   → mod lưu lại `Item` + custom name của item đó, rồi tự gửi `/dotpha`.
3. Khi chat báo *"đột phá thất bại"* hoặc *"đột phá thành công lên"* (mọi kiểu
   font, kể cả bản chữ cách điệu ᴀʙᴄ — xem mục "Giải mã font cách điệu" bên dưới)
   → mod tự gửi lại `/dotpha`.
4. Khi chat báo *"...để vượt qua thiên kiếp"* → mod tự **dùng bùa** (click chuột
   phải vào item đã lưu), rồi gửi `/dokiep`. (**Không** dùng `/tusat` nữa — tự sát
   khiến nhân vật rơi vào trạng thái "chết", làm `/dokiep` gửi ngay sau đó bị từ chối.)
5. Khi chat báo *"thất bại trong độ lôi kiếp"* → mod quay lại gửi `/dotpha`
   (tiếp tục vòng lặp chính).
6. Khi chat báo *"độ kiếp thành công"* / *"đột phá cảnh giới"* / *"sống sót qua
   độ lôi kiếp"* → mod gửi `/dotpha`, tiếp tục vòng lặp.
7. Nhấn `]` lần nữa → **TẮT** macro. Macro cũng **tự động tắt khi bạn rời server**
   (disconnect).

### Delay giữa các hành động

Mọi hành động (dùng item, gửi lệnh) đi qua một hàng đợi có độ trễ cố định giữa
mỗi bước (`COMMAND_DELAY_TICKS` trong `MacroController.java`, mặc định **12
tick ≈ 0.6 giây**), thay vì bắn liên tiếp trong cùng 1 tick. Đổi hằng số này
nếu muốn nhanh/chậm hơn.

### Giải mã font cách điệu

Nhiều server "tu tiên" hiển thị chữ bằng font Unicode "small caps" cách điệu
(vd `ᴊᴏᴋʜᴇʜᴇ` thay vì `jokhehe`, `ᴅᴏᴋɪᴇᴘ` thay vì `dokiep`) — kể cả trong tên
người chơi. Mod tự động **giải mã** các ký tự này về chữ Latin thường trước khi
so khớp pattern hoặc so username, nên chỉ cần viết pattern ở dạng chữ thường
bình thường trong `MacroController.java`.

### Tính năng an toàn

Trước **mỗi lần** gửi lệnh, mod so sánh item hiện đang cầm ở hotbar slot 1 với
item đã lưu lúc bật (so cả loại item lẫn custom name). Nếu bạn đổi item hoặc đổi
slot, macro **tự động tắt ngay** và báo lý do trong chat local — để tránh gửi
lệnh nhầm lên item khác.

Kết quả độ kiếp (thất bại/thành công) là tin **broadcast toàn server** kèm tên
người chơi, nên mod chỉ phản ứng nếu tin nhắn chứa đúng **username của bạn**
(hoặc dùng từ "bạn" cho các tin cá nhân) — tránh nhận nhầm kết quả của người khác.

## Build thủ công

```bash
./gradlew build
```

File `.jar` xuất ra ở `build/libs/dotphamacro-1.0.0.jar`.

## Build tự động (GitHub Actions)

Workflow `.github/workflows/build.yml` tự chạy khi push lên `main`/`master`
hoặc tạo tag `v*`:

- Mọi lần push → build và upload `.jar` làm **artifact** (tải ở tab Actions).
- Push tag dạng `v1.0.0` → tự tạo **GitHub Release** kèm file `.jar`.

## Cấu hình phiên bản

`gradle.properties`:
- `minecraft_version=1.21.4`
- `yarn_mappings=1.21.4+build.8`
- `loader_version=0.16.9`
- `fabric_version=0.119.4+1.21.4` (Fabric API)

Nếu Fabric ra bản mappings/API mới hơn, kiểm tra tại
https://fabricmc.net/develop và cập nhật các giá trị trên.

## Ghi chú kỹ thuật

- Mod hoàn toàn **client-side** (`"environment": "client"` trong `fabric.mod.json`),
  không cần cài trên server.
- Không dùng Mixin — chỉ dùng Fabric API events công khai
  (`ClientTickEvents`, `ClientReceiveMessageEvents`, `ClientPlayConnectionEvents`,
  `KeyBindingHelper`).
- Toàn bộ mẫu nhận diện chat được viết trong
  `src/client/java/com/dotphamacro/MacroController.java` — nếu server đổi câu
  chữ, chỉ cần sửa các mảng `*_PATTERNS` ở đầu file.
