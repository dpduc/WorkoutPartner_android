# Kịch bản UX: Thu thập thông tin cơ bản (Onboarding)

Kịch bản này mô tả luồng giao diện người dùng (UX) khi ứng dụng thu thập các thông tin cơ bản từ người dùng mới (Profile Setup), giúp trải nghiệm Onboarding trở nên thú vị, trực quan và giảm thiểu rào cản nhập liệu (friction).

## 1. Màn hình Nhập Tên (Name Input)
Thay vì để một ô văn bản trống, hệ thống sẽ tự động gợi ý một cái tên ngẫu nhiên vui nhộn nhưng mang năng lượng tích cực để người dùng có thể sử dụng ngay nếu không muốn nghĩ tên.

- **Tiêu đề:** Bạn muốn chúng tôi gọi bạn là gì? (What should we call you?)
- **UI:** Ô nhập liệu văn bản (TextField).
- **Placeholder/Gợi ý mặc định:** Sử dụng công thức `[Tính từ tích cực] + [Danh từ vui nhộn]`.
  - *Ví dụ:* `Mighty Banana`, `Swift Potato`, `Happy Turtle`, `Brave Avocado`.
- **Tương tác:**
  - Có một nút "Tạo tên ngẫu nhiên khác" (biểu tượng cục xúc xắc) bên cạnh ô nhập.
  - Người dùng có thể tự nhập tên tùy ý. Nếu người dùng bắt đầu nhập, gợi ý mặc định sẽ biến mất.

## 2. Màn hình Tuổi & Chiều cao (Age & Height)
Sử dụng thanh trượt (slider) hoặc thước cuộn (scroll picker) để mang lại cảm giác tương tác vật lý, thay vì yêu cầu gõ phím số.

- **Tiêu đề:** Chia sẻ một chút về chỉ số cơ thể của bạn nhé! (Tell us a bit about yourself!)
- **Tuổi (Age):**
  - **UI:** Thanh trượt (Slider) nằm ngang hoặc Ruler picker (thước cuộn).
  - **Khoảng (Range):** 13 - 100 tuổi.
  - **Mặc định (Default):** 25 tuổi.
- **Chiều cao (Height):**
  - **UI:** Thanh trượt dọc (Vertical Slider) có vạch chia centimet, có hiệu ứng haptic feedback khi trượt.
  - **Khoảng (Range):** 120 cm - 220 cm.
  - **Mặc định (Default):** 170 cm.

## 3. Màn hình Mức độ hoạt động (Activity Level)
Thay vì sử dụng 3 cấp độ khô khan như "Beginner", "Intermediate", "Advanced", ứng dụng sẽ hiển thị các thẻ (cards) lớn. Mỗi thẻ bao gồm một câu mô tả ngắn gọn, thân thiện và một hình minh họa (Illustration/Icon) tương ứng.

- **Tiêu đề:** Mức độ vận động hiện tại của bạn như thế nào? (How active are you currently?)
- **UI:** Danh sách các thẻ (Cards) có thể chọn (Single choice). Khi chọn thẻ sẽ có viền highlight màu chủ đạo.

**Các tùy chọn (Options):**

1. **Ít vận động (Sedentary)**
   - **Mô tả:** "Tôi hiếm khi tập thể dục." (I rarely do exercises).
   - **Minh họa (Illustration):** Hình ảnh một người đang ngồi trên sofa hoặc làm việc trước máy tính, tay cầm ly cà phê.
2. **Vận động nhẹ (Lightly Active)**
   - **Mô tả:** "Tôi tập nhẹ nhàng vài lần một tuần." (A few times a week).
   - **Minh họa (Illustration):** Hình ảnh một người đang đi bộ dạo hoặc tập yoga nhẹ nhàng.
3. **Vận động thường xuyên (Active)**
   - **Mô tả:** "Tôi tập luyện thường xuyên." (Frequently active).
   - **Minh họa (Illustration):** Hình ảnh một người đang chạy bộ hoặc đạp xe vã mồ hôi.
4. **Rất năng động (Very Active)**
   - **Mô tả:** "Tập luyện là đam mê hàng ngày của tôi." (Working out is my daily passion).
   - **Minh họa (Illustration):** Hình ảnh một người đang đẩy tạ nặng hoặc thực hiện một động tác thể thao cường độ cao.

## Lợi ích của luồng UX này
- **Giảm tải nhận thức (Cognitive load):** Tránh việc bắt người dùng gõ phím nhiều. Thanh trượt và lựa chọn thẻ tốn ít công sức hơn.
- **Tăng sự thích thú (Delight):** Các tên gọi ngẫu nhiên và hình minh họa trực quan tạo cảm giác gần gũi, thân thiện, giúp người dùng không cảm thấy như đang điền một tờ khai y tế khô khan.
