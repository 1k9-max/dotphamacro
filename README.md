# DotPha Macro (Fabric 1.21.4, client-side)

Mod client-side tự động hóa vòng lặp **đột phá / độ kiếp** dựa theo nội dung chat của server.

⚠️ **Lưu ý quan trọng:** đây là macro tự động gửi lệnh dựa theo chat. Nhiều server coi
đây là hành vi macro/auto và có thể **cấm** trong luật chơi — bạn tự chịu trách nhiệm
kiểm tra luật server trước khi dùng.

## Cách hoạt động

1. Nhấn phím `]` (mặc định, đổi được trong `Controls`) → macro **BẬT (armed)**.
2. Cầm item cần dùng ở **hotbar slot 1**, sau đó **click chuột trái** một lần
   → mod lưu lại `Item` + custom name của item đó, rồi tự gửi `/dotpha`.
3. Khi chat báo *"đột phá thất bại"* hoặc *"đột phá thành công lên"* (cả bản chữ
   thường lẫn bản ký tự cách điệu ᴀʙᴄ) → mod tự gửi lại `/dotpha`.
4. Khi chat báo *"HÃY DÙNG /dokiep ĐỂ VƯỢT QUA THIÊN KIẾP"* → mod tự **click chuột
   phải** vào item đã lưu, gửi `/tusat`, rồi gửi `/dokiep`.
5. Khi chat báo *"thất bại trong độ lôi kiếp"* → mod quay lại gửi `/dotpha`
   (tiếp tục vòng lặp chính).
6. Khi chat báo *"độ kiếp thành công"* / *"đột phá cảnh giới"* / *"sống sót qua
   độ lôi kiếp"* → mod gửi `/dotpha`, tiếp tục vòng lặp.
7. Nhấn `]` lần nữa → **TẮT** macro. Macro cũng **tự động tắt khi bạn rời server**
   (disconnect).

### Tính năng an toàn

Trước **mỗi lần** gửi lệnh, mod so sánh item hiện đang cầm ở hotbar slot 1 với
item đã lưu lúc bật (so cả loại item lẫn custom name). Nếu bạn đổi item hoặc đổi
slot, macro **tự động tắt ngay** và báo lý do trong chat local — để tránh gửi
lệnh nhầm lên item khác.

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
