# Hệ Thống Bài Tập Tích Hợp Pose Estimation Theo BMI & Thể Lực

Tài liệu này nghiên cứu cơ sở động học, phân tích khả năng nhận diện chuyển động bằng thị giác máy tính (**Google MediaPipe Pose Landmarker**), và thiết kế hệ sinh thái bài tập được cá nhân hóa theo **Chỉ số khối cơ thể (BMI)** và **Mức độ vận động (Activity Level)** cho ứng dụng **Workout Partner**.

Hệ thống bài tập chia thành **2 loại chính**:

| Loại | Tên gọi | Mục đích | Cơ chế |
| :--- | :--- | :--- | :--- |
| **Phần A** | **Bài Tập Truyền Thống (Structured Routines)** | Rèn luyện thể lực hàng ngày theo kế hoạch cố định | Thực hiện đúng số rep/set theo thiết kế, nghỉ theo thời gian cố định |
| **Phần B** | **Bài Tập AMRAP (As Many Reps/Rounds As Possible)** | Đánh giá & tracking năng lực vận động tối đa | Thực hiện tối đa số round/rep trong giới hạn thời gian |

---

## Kiến Thức Nền Tảng Chung

### 1. Thách Thức Của Pose Estimation Trên Thiết Bị Di Động
Khi người dùng đặt điện thoại để tập luyện, camera trước (Front Camera) phải xử lý khung hình góc rộng trong điều kiện môi trường thực tế (ánh sáng thay đổi, không gian hẹp, khoảng cách 1.8m – 2.5m).

Để một bài tập có thể **đếm Rep chính xác** và **chấm Form Score đáng tin cậy**, động tác đó phải thỏa mãn 3 tiêu chuẩn thị giác máy tính:

1. **Tính phân tách khớp cao (High Joint Distinctiveness):**
   * Các điểm mốc cơ thể (Landmarks) quan trọng (Vai, Khuỷu tay, Cổ tay, Hông, Đầu gối, Cổ chân) không bị che khuất bởi các bộ phận khác (ít xảy ra hiện tượng *Self-Occlusion*).
2. **Biên độ thay đổi góc lớn (Wide Angular Amplitude):**
   * Khớp chính (Vertex Joint) phải có độ chênh lệch góc tối thiểu $\Delta \theta \ge 40^\circ - 60^\circ$ giữa trạng thái nghỉ (Rest/Extended) và trạng thái gập cơ tối đa (Contracted/Inflection Point).
3. **Điểm đảo chiều rõ ràng (Inflection Point Stability):**
   * Chuyển động có 2 pha đối xứng rõ rệt:
     * **Pha ly tâm (Eccentric):** Góc khớp co lại hoặc mở rộng dần.
     * **Pha hướng tâm (Concentric):** Góc khớp trở về vị trí xuất phát.

---

### 2. Logic Pose Estimation Cho 5 Nhóm Động Tác Cốt Lõi

Hệ thống theo dõi sử dụng mô hình **MediaPipe Pose 33 3D Landmarks** được ánh xạ vào bộ đếm trạng thái (`RepCounter` State Machine) với 2 ngưỡng:
* **Ngưỡng đếm Rep (`repThresholdDegrees`):** Góc tối thiểu để ghi nhận 1 rep hợp lệ.
* **Ngưỡng chuẩn Form (`formThresholdDegrees`):** Góc đạt chuẩn biên độ chuyển động (Full Range of Motion - ROM) để cộng điểm **Good Form**.

| Động tác | Khớp đo chính (A - Vertex - C) | Chiều góc khi thực hiện | Ngưỡng đếm Rep | Ngưỡng Good Form | Góc đặt Camera tối ưu | Lỗi Form AI cần phát hiện |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **SQUAT** | **Hông – Đầu gối – Cổ chân** | `DECREASING` (Giảm dần) | $\le 130^\circ$ | $\le 100^\circ$ (Đùi song song sàn) | Góc chéo $45^\circ$ hoặc chính diện | Squat nửa vời (Quarter squat), chụm gối (Knee valgus), gập lưng quá mức |
| **PUSH-UP** | **Vai – Khuỷu tay – Cổ tay** | `DECREASING` (Giảm dần) | $\le 130^\circ$ | $\le 90^\circ$ (Ngực gần chạm sàn) | Góc nghiêng $45^\circ$ ngang thân | Chưa hạ đủ sâu, võng lưng (Hông tụt), chổng mông |
| **SIT-UP** | **Vai – Hông – Đầu gối** | `DECREASING` (Giảm dần) | $\le 130^\circ$ | $\le 70^\circ$ (Thân trên dựng đứng) | Góc nghiêng ngang $90^\circ$ hoặc $45^\circ$ | Dùng đà cổ, giật lưng, không nâng lưng khỏi mặt sàn |
| **LUNGE** | **Hông – Đầu gối trước – Cổ chân** | `DECREASING` (Giảm dần) | $\le 130^\circ$ | $\le 100^\circ$ (Gối trước vuông góc) | Góc chéo $45^\circ$ | Gối trước vượt quá mũi chân, thân trên đổ về trước |
| **JUMPING JACK** | **Khuỷu tay – Vai – Hông** | `INCREASING` (Tăng dần) | $\ge 90^\circ$ | $\ge 150^\circ$ (Tay chạm qua đầu) | Góc chính diện $0^\circ$ | Tay giơ nửa vời, chân không mở rộng đủ nhịp |

---

### 3. Công Thức Tính Form Score Chuẩn Hóa
$$FormScore = \text{round}\left(\frac{\text{Số Rep đạt ngưỡng Form}}{\text{Tổng số Rep thực hiện}} \times 100\right)$$
* **Good Set (Set đạt chuẩn):** $\text{Actual Reps} \ge \text{Target Reps}$ **VÀ** $FormScore \ge 75\%$.

---

### 4. Ma Trận Phân Bổ Theo BMI & Cường Độ Vận Động

Chỉ số BMI (Body Mass Index) kết hợp với Thói quen vận động quyết định áp lực cơ học lên khớp và hệ tim mạch:

$$\text{BMI} = \frac{\text{Cân nặng (kg)}}{\left(\text{Chiều cao (m)}\right)^2}$$

#### Phân Nhóm Thể Trạng:
1. **Nhóm BMI < 18.5 (Thiếu cân / Gầy):**
   * *Mục tiêu:* Tăng cơ (Hypertrophy), củng cố sức mạnh nền tảng, hạn chế các bài Cardio tiêu hao năng lượng quá mức.
   * *Đặc tính:* Nghỉ dài giữa các set ($45s - 60s$), tempo chuyển động chậm có kiểm soát (TUT - Time Under Tension).
2. **Nhóm BMI 18.5 – 24.9 (Bình thường / Cân đối):**
   * *Mục tiêu:* Tối ưu hóa sức bền tim mạch, mật độ cơ bắp, độ linh hoạt toàn diện (HIIT, Calisthenics Circuit).
   * *Đặc tính:* Tốc độ đẩy cao, nghỉ ngắn ($20s - 30s$), kết hợp cardio nhịp tim cao.
3. **Nhóm BMI 25.0 – 29.9 (Thừa cân):**
   * *Mục tiêu:* Kích hoạt trao đổi chất, đốt mỡ an toàn, tăng sức bền cơ thể.
   * *Đặc tính:* Tần suất vừa phải, xen kẽ động tác toàn thân và nghỉ chủ động, giảm áp lực va đập lên khớp gối.
4. **Nhóm BMI $\ge$ 30.0 (Béo phì / Hạn chế khớp):**
   * *Mục tiêu:* Bảo vệ an toàn tuyệt đối cho hệ cơ xương khớp (sụn chêm gối, đĩa đệm cột sống), tạo thói quen vận động tim mạch nhẹ nhàng.
   * *Đặc tính:* **100% Low-Impact (Không nhảy)**, thay thế jumping jack bằng step-jack hoặc nâng cao đùi bước chậm, giảm góc uốn gối sâu.

---
---

# PHẦN A: BÀI TẬP TRUYỀN THỐNG (STRUCTURED ROUTINES)

> **Mô tả:** Các bộ bài tập có cấu trúc cố định (số rep, số round, thời gian nghỉ xác định trước). Người dùng thực hiện theo kịch bản đã thiết kế. Hệ thống đếm rep và chấm form score cho từng set.
>
> **Mục đích:** Rèn luyện thể lực hàng ngày, xây dựng thói quen vận động, phát triển cơ bắp và sức bền có kiểm soát.

---

### Ma Trận Lựa Chọn 10 Bộ Bài Tập Truyền Thống:

| Mã Routine | Tên Routine | Phân loại BMI | Cường độ | Thời lượng | Đặc tính chuyển động |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **RT-01** | **Joint-Safe Mobility & Tone** | $\ge 30.0$ (Béo phì) | Mới bắt đầu (Sedentary) | 10–12 phút | Low-impact, không nhảy, nhịp thở đều |
| **RT-02** | **Gentle Low-Impact Cardio** | $\ge 30.0$ & 25–29.9 | Nhẹ nhàng (Beginner) | 12–15 phút | Nhịp tim vừa, Squat nửa biên độ, bảo vệ khớp |
| **RT-03** | **Lean Muscle Upper & Core** | $< 18.5$ (Thiếu cân) | Mới / Trung bình | 14–16 phút | Chống đẩy kiểm soát, Sit-up siết cơ, nghỉ đủ |
| **RT-04** | **Lower Body Muscle Builder** | $< 18.5$ & 18.5–24.9 | Trung bình (Moderate) | 15–18 phút | Tải trọng chân sâu, tập trung form chuẩn |
| **RT-05** | **Steady Metabolic Burner** | 25.0–29.9 (Thừa cân) | Trung bình (Moderate) | 15–18 phút | Đốt mỡ ngắt quãng, cardio phối hợp sức bền |
| **RT-06** | **Core Stability & Flow** | 25.0–29.9 & 18.5–24.9 | Trung bình | 14–16 phút | Siết cơ lõi, Sit-up chuẩn form, nâng nhịp tim |
| **RT-07** | **Full Body Basics Plus** | 18.5–24.9 (Chuẩn) | Trung bình (Moderate) | 15–18 phút | Phối hợp toàn diện 4 nhóm cơ chính |
| **RT-08** | **Athletic Power Circuit** | 18.5–24.9 (Chuẩn) | Nâng cao (Active) | 16–20 phút | Biên độ tối đa, tốc độ rep nhanh, nghỉ ngắn |
| **RT-09** | **High-Intensity Tabata/HIIT** | 18.5–24.9 & 25.0–27.0 | Rất cao (Advanced) | 12–15 phút | Đốt calo đỉnh cao, Jumping Jack + Push-up liên hoàn |
| **RT-10** | **Express Desk-Worker Reset** | Mọi nhóm BMI | Nhanh / Tiện lợi | 10 phút | Giải tỏa đau mỏi cột sống, kích hoạt chuyển hóa |

---

## Thiết Kế Chi Tiết 10 Bộ Bài Tập Truyền Thống

---

### Routine 01: Joint-Safe Mobility & Tone
> **Mục tiêu:** Kích hoạt cơ thể cho người thừa cân, béo phì hoặc người mới làm quen vận động. Không gây áp lực lên khớp gối và cột sống.  
> **Chỉ số khuyến nghị:** BMI $\ge 30.0$ | Mức độ: Sedentary / Beginner  
> **Tổng thời lượng:** ~11 phút (2 Rounds)

* **Cấu trúc thực hiện:**
  * **Khởi động (2 phút):** Xoay nhẹ khớp vai, cổ chân, vươn vai hít thở sâu.
  * **Round 1:**
    1. **Squat nhẹ (Half Squat):** 8 reps *(Góc gối chỉ cần tới $120^\circ$, giữ thẳng lưng)* | Nghỉ: 35 giây.
    2. **Push-up (Incline / Chống đẩy tường hoặc gối):** 6 reps *(Góc khuỷu tay $\le 110^\circ$)* | Nghỉ: 35 giây.
    3. **Step Jack (Jumping Jack không bật nhảy, bước chân từng bên):** 15 reps | Nghỉ: 45 giây.
  * **Round 2:**
    4. **Squat nhẹ (Half Squat):** 8 reps | Nghỉ: 35 giây.
    5. **Push-up (Incline):** 6 reps | Nghỉ: 35 giây.
    6. **Step Jack:** 15 reps | Nghỉ: 60 giây.
  * **Hạ nhiệt & Thả lỏng (1.5 phút):** Giãn cơ đùi trước và bả vai.

---

### Routine 02: Gentle Low-Impact Cardio
> **Mục tiêu:** Tăng tuần hoàn máu, đốt calo an toàn cho thể trạng nặng cân mà không gián đoạn nhịp thở.  
> **Chỉ số khuyến nghị:** BMI 28.0 – 33.0 | Mức độ: Beginner  
> **Tổng thời lượng:** ~13 phút (2 Rounds)

* **Cấu trúc thực hiện:**
  * **Khởi động (2 phút):** Đánh tay chéo ngực, nâng gối thấp tại chỗ.
  * **Round 1:**
    1. **Squat (Tempo chậm 3 giây xuống - 1 giây lên):** 10 reps | Nghỉ: 30 giây.
    2. **Step Jack:** 20 reps | Nghỉ: 30 giây.
    3. **Lunge ngắn bước (Shallow Lunge):** 6 reps mỗi bên *(tổng 12 reps)* | Nghỉ: 40 giây.
  * **Round 2:**
    4. **Squat:** 10 reps | Nghỉ: 30 giây.
    5. **Push-up kê cao tay:** 8 reps | Nghỉ: 30 giây.
    6. **Step Jack:** 20 reps | Nghỉ: 50 giây.
  * **Hạ nhiệt (1.5 phút):** Kéo giãn bắp chân và hông.

---

### Routine 03: Lean Muscle Upper & Core
> **Mục tiêu:** Kích thích phì đại cơ (Hypertrophy) thân trên và cơ bụng cho người gầy, thiếu cân cần săn chắc cơ thể.  
> **Chỉ số khuyến nghị:** BMI $< 18.5$ | Mức độ: Beginner đến Intermediate  
> **Tổng thời lượng:** ~15 phút (3 Rounds)

* **Cấu trúc thực hiện:**
  * **Khởi động (2 phút):** Xoay khớp vai, cổ tay, gập duỗi khuỷu tay.
  * **Vòng lặp 3 Rounds (Mỗi round nghỉ 45 giây):**
    1. **Push-up chuẩn:** 8 – 10 reps *(Kiểm soát góc khuỷu tay chạm $90^\circ$)* | Nghỉ: 45 giây.
    2. **Sit-up siết bụng:** 10 – 12 reps *(Gập thân trên chạm góc $70^\circ$)* | Nghỉ: 45 giây.
    3. **Push-up (Giữ 1 giây ở đáy):** 6 reps | Nghỉ: 60 giây.
  * **Hạ nhiệt (2 phút):** Rắn hổ mang (Cobra stretch) giãn cơ bụng và ngực.

---

### Routine 04: Lower Body Muscle Builder
> **Mục tiêu:** Tăng kích thước cơ đùi, mông và sức chịu tải của đôi chân cho thể trạng gầy hoặc cân đối.  
> **Chỉ số khuyến nghị:** BMI $< 20.0$ | Mức độ: Intermediate  
> **Tổng thời lượng:** ~16 phút (3 Rounds)

* **Cấu trúc thực hiện:**
  * **Khởi động (2 phút):** Xoay hông, ép dẻo chân ngang và dọc.
  * **Vòng lặp 3 Rounds (Mỗi round nghỉ 45 giây):**
    1. **Deep Squat (Squat sâu):** 12 reps *(Góc gối $\le 95^\circ$, hông mở rộng)* | Nghỉ: 40 giây.
    2. **Lunge chân trước:** 10 reps mỗi bên *(Góc gối trước $90^\circ$)* | Nghỉ: 40 giây.
    3. **Deep Squat:** 10 reps | Nghỉ: 60 giây.
  * **Hạ nhiệt (2 phút):** Căng giãn cơ đùi sau (Hamstring stretch) và bắp chuối.

---

### Routine 05: Steady Metabolic Burner
> **Mục tiêu:** Kích hoạt cơ chế trao đổi chất liên tục, tiêu hao mỡ thừa ở người thừa cân với nhịp tim ổn định.  
> **Chỉ số khuyến nghị:** BMI 25.0 – 29.9 | Mức độ: Moderate  
> **Tổng thời lượng:** ~16 phút (3 Rounds)

* **Cấu trúc thực hiện:**
  * **Khởi động (2 phút):** Xoay khớp, bật nhảy nhấp chân nhẹ tại chỗ.
  * **Vòng lặp 3 Rounds:**
    1. **Jumping Jack:** 25 reps | Nghỉ: 25 giây.
    2. **Squat nhịp đều:** 12 reps | Nghỉ: 30 giây.
    3. **Push-up:** 8 reps | Nghỉ: 30 giây.
    4. **Sit-up:** 10 reps | Nghỉ: 45 giây.
  * **Hạ nhiệt (2 phút):** Điều hòa nhịp thở, giãn cơ toàn thân.

---

### Routine 06: Core Stability & Flow
> **Mục tiêu:** Củng cố nhóm cơ lõi (Core) giúp bảo vệ cột sống thắt lưng, cải thiện tư thế đứng và săn chắc vòng eo.  
> **Chỉ số khuyến nghị:** BMI 23.0 – 28.0 | Mức độ: Moderate  
> **Tổng thời lượng:** ~14 phút (3 Rounds)

* **Cấu trúc thực hiện:**
  * **Khởi động (2 phút):** Xoay lườn, uốn người hình con mèo - con bò (Cat-Cow).
  * **Vòng lặp 3 Rounds:**
    1. **Sit-up:** 14 reps *(Tập trung siết bụng dưới khi nâng người)* | Nghỉ: 30 giây.
    2. **Squat giữ thăng bằng:** 12 reps *(Dừng 1 giây ở đáy)* | Nghỉ: 30 giây.
    3. **Jumping Jack nhịp chậm:** 20 reps | Nghỉ: 40 giây.
  * **Hạ nhiệt (1.5 phút):** Ôm gối sát ngực, vặn mình nằm sàn.

---

### Routine 07: Full Body Basics Plus
> **Mục tiêu:** Bộ bài tập tiêu chuẩn hoàn thiện, rèn luyện đồng thời 4 nhóm cơ chính: Ngực/Vai, Bụng, Đùi/Mông và Tim mạch.  
> **Chỉ số khuyến nghị:** BMI 18.5 – 24.9 | Mức độ: Moderate  
> **Tổng thời lượng:** ~16 phút (3 Rounds)

* **Cấu trúc thực hiện:**
  * **Khởi động (2 phút):** Toàn thân nhịp nhàng.
  * **Vòng lặp 3 Rounds:**
    1. **Squat:** 12 reps | Nghỉ: 25 giây.
    2. **Push-up:** 10 reps | Nghỉ: 25 giây.
    3. **Sit-up:** 12 reps | Nghỉ: 25 giây.
    4. **Jumping Jack:** 25 reps | Nghỉ: 45 giây.
  * **Hạ nhiệt (2 phút):** Thả lỏng các khớp chi.

---

### Routine 08: Athletic Power Circuit
> **Mục tiêu:** Nâng cao sức bật, thể lực và khả năng chuyển đổi trạng thái cơ thể liên tục cho người có nền tảng thể lực tốt.  
> **Chỉ số khuyến nghị:** BMI 19.0 – 24.5 | Mức độ: Active / Advanced  
> **Tổng thời lượng:** ~18 phút (3 Rounds)

* **Cấu trúc thực hiện:**
  * **Khởi động (2 phút):** Khởi động khớp tích cực.
  * **Vòng lặp 3 Rounds:**
    1. **Lunge đổi chân liên tục:** 14 reps *(7 reps/chân)* | Nghỉ: 20 giây.
    2. **Push-up bùng nổ:** 12 reps | Nghỉ: 20 giây.
    3. **Deep Squat:** 15 reps | Nghỉ: 20 giây.
    4. **Jumping Jack tốc độ:** 30 reps | Nghỉ: 40 giây.
  * **Hạ nhiệt (2 phút):** Ép dẻo sâu cơ đùi và vai.

---

### Routine 09: High-Intensity Metabolic HIIT
> **Mục tiêu:** Đốt cháy năng lượng cực đại trong thời gian ngắn (hiệu ứng EPOC kéo dài sau tập), tăng sức bền tim mạch tối đa.  
> **Chỉ số khuyến nghị:** BMI 18.5 – 26.0 | Mức độ: Advanced (Không khuyến nghị cho người bệnh tim mạch)  
> **Tổng thời lượng:** ~14 phút (4 Rounds cường độ cao)

* **Cấu trúc thực hiện:**
  * **Khởi động (2 phút):** Kích hoạt cơ sâu.
  * **Vòng lặp 4 Rounds (Nghỉ cực ngắn):**
    1. **Jumping Jack tốc độ tối đa:** 35 reps | Nghỉ: 15 giây.
    2. **Push-up nhanh chuẩn form:** 12 reps | Nghỉ: 15 giây.
    3. **Squat nhanh:** 15 reps | Nghỉ: 15 giây.
    4. **Sit-up nhanh:** 12 reps | Nghỉ: 35 giây.
  * **Hạ nhiệt (2.5 phút):** Đi bộ chậm, hít thở điều hòa nhịp tim về bình thường.

---

### Routine 10: Express Desk-Worker Reset
> **Mục tiêu:** Bài tập nhanh 10 phút giữa giờ làm việc giúp kích hoạt tuần hoàn máu, giải tỏa căng cứng khớp hông, gù lưng do ngồi lâu trước máy tính.  
> **Chỉ số khuyến nghị:** Phù hợp cho **mọi chỉ số BMI** | Thời gian: Bất kỳ thời điểm nào trong ngày  
> **Tổng thời lượng:** ~10 phút (2 Rounds)

* **Cấu trúc thực hiện:**
  * **Khởi động (1 phút):** Xoay tròn khớp vai, nghiêng đầu, mở ngực.
  * **Round 1:**
    1. **Squat mở rộng hông:** 10 reps *(Giải phóng khớp háng)* | Nghỉ: 25 giây.
    2. **Jumping Jack nhẹ / Step Jack:** 20 reps *(Đẩy máu về tim)* | Nghỉ: 25 giây.
    3. **Lunge bước rộng:** 8 reps *(4 reps/chân - kéo giãn gập hông Hip Flexor)* | Nghỉ: 35 giây.
  * **Round 2:**
    4. **Squat mở rộng hông:** 10 reps | Nghỉ: 25 giây.
    5. **Jumping Jack / Step Jack:** 20 reps | Nghỉ: 25 giây.
    6. **Sit-up nhẹ nhàng:** 10 reps | Nghỉ: 45 giây.
  * **Hạ nhiệt (1.5 phút):** Vươn tay kéo giãn liên sườn và cột sống.

---
---

# PHẦN B: BÀI TẬP AMRAP (AS MANY REPS/ROUNDS AS POSSIBLE)

> **Mô tả:** AMRAP là phương pháp tập luyện trong đó người dùng thực hiện **tối đa số round (vòng lặp)** hoặc **tối đa số rep (lần lặp lại)** của một chuỗi bài tập trong **giới hạn thời gian** cố định. Không có thời gian nghỉ được lập trình — người dùng tự quyết định nhịp nghỉ dựa trên thể lực.
>
> **Mục đích:** Đánh giá và theo dõi (tracking) **năng lực vận động tối đa** của người dùng theo thời gian. Điểm AMRAP là thước đo khách quan, có thể so sánh qua các lần thực hiện để thấy sự tiến bộ.

---

## Nguyên Lý AMRAP & Cơ Chế Scoring

### Khác Biệt Cốt Lõi Giữa AMRAP và Bài Tập Truyền Thống

| Tiêu chí | Structured Routine (Phần A) | AMRAP (Phần B) |
| :--- | :--- | :--- |
| **Mục tiêu** | Hoàn thành đúng kế hoạch tập | Đạt nhiều round/rep nhất có thể |
| **Thời gian** | Tùy thuộc tốc độ tập | **Cố định** (Time Cap) |
| **Số Rep** | **Cố định** theo thiết kế | Không giới hạn — tập tới khi hết giờ |
| **Nghỉ giữa set** | Theo thời gian quy định | **Tự quyết định** — nghỉ khi cần |
| **Đo lường kết quả** | Form Score (%) + hoàn thành/không | **Score = Tổng Rounds hoàn thành + Reps thừa** |
| **Ứng dụng** | Rèn luyện hàng ngày | Benchmark test — đo thể lực định kỳ |

### Cách Tính Điểm AMRAP (AMRAP Score)

Người dùng thực hiện một chuỗi circuit lặp đi lặp lại cho đến khi hết thời gian. Điểm được tính:

$$\text{AMRAP Score} = R \times N + r$$

Trong đó:
* $R$ = Số **round hoàn chỉnh** (hoàn thành tất cả bài tập trong circuit 1 lần)
* $N$ = Tổng số rep trong 1 round hoàn chỉnh
* $r$ = Số **rep thừa** (rep đã thực hiện của round chưa hoàn thành khi hết giờ)

> **Ví dụ:** Circuit gồm 5 Push-up + 10 Squat + 15 Jumping Jack (tổng 1 round = 30 rep). Trong 10 phút, người dùng hoàn thành 4 round đầy đủ + thêm 5 Push-up và 7 Squat trước khi hết giờ.
> * $R = 4$, $N = 30$, $r = 5 + 7 = 12$
> * **AMRAP Score = 4 × 30 + 12 = 132 rep**

### Quy Tắc Chấm Form Trong AMRAP

Trong AMRAP, vì không có nghỉ bắt buộc, người dùng dễ hy sinh form khi mệt. Hệ thống pose estimation áp dụng quy tắc **AMRWPFAP** (As Many Reps With Perfect Form As Possible):

1. **Rep chỉ được đếm khi đạt ngưỡng `repThresholdDegrees`** — rep không đủ biên độ (quarter squat, push-up nửa vời) **không được tính**.
2. **Form Score vẫn được track** song song nhưng **không ảnh hưởng đến AMRAP Score** — nó phục vụ mục đích giáo dục và cảnh báo cho người dùng.
3. **Cảnh báo giảm form:** Khi Form Score giảm xuống dưới 50% trong 1 round, hiển thị cảnh báo nhẹ nhàng: *"Hãy giảm tốc độ và tập trung vào form chính xác"*.

---

## Thiết Kế 6 Bộ AMRAP Cho Các Cường Độ Vận Động

### Ma Trận AMRAP Theo BMI & Cường Độ:

| Mã AMRAP | Tên | Phân loại BMI | Cường độ | Time Cap | Circuit (1 Round) | Mục tiêu đánh giá |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **AM-01** | **Gentle Starter Test** | $\ge 28.0$ | Beginner | 8 phút | 3 bài, low-impact | Nền tảng vận động cơ bản |
| **AM-02** | **Foundation Benchmark** | Mọi BMI | Beginner – Moderate | 10 phút | 3 bài cơ bản | Sức bền cơ bản toàn thân |
| **AM-03** | **Lean Endurance Check** | $< 20.0$ | Moderate | 12 phút | 3 bài, tập trung thân trên | Sức bền cơ thân trên |
| **AM-04** | **Full-Body Capacity Test** | 18.5 – 27.0 | Moderate – Active | 15 phút | 5 bài toàn thân | Năng lực toàn diện |
| **AM-05** | **Athletic Power Benchmark** | 18.5 – 25.0 | Active – Advanced | 15 phút | 5 bài, rep cao | Công suất & sức bền tối đa |
| **AM-06** | **Max Output Sprint** | 18.5 – 25.0 | Advanced | 10 phút | 4 bài, cường độ bùng nổ | Giới hạn vận động cực đại |

---

### AMRAP 01: Gentle Starter Test
> **Mục tiêu:** Đánh giá khả năng vận động cơ bản cho người thừa cân hoặc mới bắt đầu. Toàn bộ low-impact, không nhảy.  
> **Chỉ số khuyến nghị:** BMI $\ge 28.0$ | Mức độ: Sedentary / Beginner  
> **Time Cap:** 8 phút

* **Khởi động (2 phút):** Xoay khớp nhẹ, hít thở sâu.
* **Circuit (lặp liên tục trong 8 phút):**

| # | Bài tập | Reps / Round | Ghi chú |
| :--- | :--- | :--- | :--- |
| 1 | **Squat nhẹ (Half Squat)** | 5 reps | Góc gối tới $120^\circ$, không cần sâu |
| 2 | **Push-up (Incline / Gối)** | 3 reps | Chống đẩy kê cao tay hoặc quỳ gối |
| 3 | **Step Jack** | 10 reps | Bước chân sang bên, không bật nhảy |

* **Tổng rep / Round:** 18 rep
* **Hạ nhiệt (2 phút):** Giãn cơ nhẹ nhàng.

**Bảng tham chiếu tiến bộ:**

| Mức | Rounds hoàn thành (8 phút) | AMRAP Score | Đánh giá |
| :--- | :--- | :--- | :--- |
| Cần cải thiện | < 3 rounds | < 54 | Tiếp tục rèn luyện nền tảng |
| Đạt yêu cầu | 3 – 4 rounds | 54 – 72 | Cơ sở tốt, tăng dần biên độ |
| Tốt | 5 – 6 rounds | 90 – 108 | Sẵn sàng lên AM-02 |
| Xuất sắc | 7+ rounds | 126+ | Thể lực vượt mức, chuyển AM-02/AM-04 |

---

### AMRAP 02: Foundation Benchmark
> **Mục tiêu:** Bài test chuẩn cho mọi người dùng mới — đo sức bền cơ bản toàn thân bằng 3 bài tập nền tảng.  
> **Chỉ số khuyến nghị:** Mọi BMI | Mức độ: Beginner – Moderate  
> **Time Cap:** 10 phút

* **Khởi động (2 phút):** Xoay khớp toàn thân, nâng gối nhẹ.
* **Circuit (lặp liên tục trong 10 phút):**

| # | Bài tập | Reps / Round | Ghi chú |
| :--- | :--- | :--- | :--- |
| 1 | **Push-up** | 5 reps | Chuẩn form, hạ ngực gần sàn |
| 2 | **Squat** | 10 reps | Full ROM, đùi song song sàn |
| 3 | **Sit-up** | 10 reps | Siết bụng, nâng vai khỏi mặt sàn |

* **Tổng rep / Round:** 25 rep
* **Hạ nhiệt (2 phút):** Giãn cơ bụng, đùi, vai.

**Bảng tham chiếu tiến bộ:**

| Mức | Rounds hoàn thành (10 phút) | AMRAP Score | Đánh giá |
| :--- | :--- | :--- | :--- |
| Cần cải thiện | < 3 rounds | < 75 | Tập trung form trước, tốc độ sau |
| Đạt yêu cầu | 3 – 5 rounds | 75 – 125 | Nền tảng vững chắc |
| Tốt | 6 – 7 rounds | 150 – 175 | Sẵn sàng lên AM-04 |
| Xuất sắc | 8+ rounds | 200+ | Thể lực nền tảng cao |

> **Lưu ý:** AM-02 là **bài test được khuyên dùng để đo baseline** cho mọi người dùng mới khi lần đầu sử dụng ứng dụng. Kết quả này giúp hệ thống gợi ý Routine phù hợp.

---

### AMRAP 03: Lean Endurance Check
> **Mục tiêu:** Đánh giá sức bền cơ thân trên cho người gầy/thiếu cân. Tập trung Push-up và Sit-up để đo khả năng duy trì cơ lực.  
> **Chỉ số khuyến nghị:** BMI $< 20.0$ | Mức độ: Moderate  
> **Time Cap:** 12 phút

* **Khởi động (2 phút):** Xoay vai, gập duỗi khuỷu tay, cat-cow stretch.
* **Circuit (lặp liên tục trong 12 phút):**

| # | Bài tập | Reps / Round | Ghi chú |
| :--- | :--- | :--- | :--- |
| 1 | **Push-up** | 8 reps | Kiểm soát 2 giây xuống, 1 giây lên |
| 2 | **Sit-up** | 12 reps | Siết bụng toàn bộ, chạm gối |
| 3 | **Push-up (tay hẹp)** | 5 reps | Tay đặt sát vai, nhấn mạnh cơ tam đầu |

* **Tổng rep / Round:** 25 rep
* **Hạ nhiệt (2 phút):** Cobra stretch, giãn ngực và vai.

**Bảng tham chiếu tiến bộ:**

| Mức | Rounds hoàn thành (12 phút) | AMRAP Score | Đánh giá |
| :--- | :--- | :--- | :--- |
| Cần cải thiện | < 3 rounds | < 75 | Cần tập Routine RT-03 trước |
| Đạt yêu cầu | 3 – 5 rounds | 75 – 125 | Sức bền thân trên ổn |
| Tốt | 6 – 7 rounds | 150 – 175 | Cơ thân trên phát triển tốt |
| Xuất sắc | 8+ rounds | 200+ | Sẵn sàng AM-05 |

---

### AMRAP 04: Full-Body Capacity Test
> **Mục tiêu:** Bài test toàn diện — đo năng lực phối hợp tất cả 5 nhóm động tác cùng lúc. Đây là thước đo "fitness level" tổng thể đáng tin cậy nhất.  
> **Chỉ số khuyến nghị:** BMI 18.5 – 27.0 | Mức độ: Moderate – Active  
> **Time Cap:** 15 phút

* **Khởi động (3 phút):** Khởi động toàn thân kỹ lưỡng (khớp, cardio nhẹ).
* **Circuit (lặp liên tục trong 15 phút):**

| # | Bài tập | Reps / Round | Ghi chú |
| :--- | :--- | :--- | :--- |
| 1 | **Squat** | 10 reps | Full ROM |
| 2 | **Push-up** | 8 reps | Chuẩn form |
| 3 | **Lunge** | 10 reps *(5/chân)* | Gối trước $90^\circ$ |
| 4 | **Sit-up** | 10 reps | Siết bụng đầy đủ |
| 5 | **Jumping Jack** | 20 reps | Biên độ tay đầy đủ trên đầu |

* **Tổng rep / Round:** 58 rep
* **Hạ nhiệt (3 phút):** Giãn cơ toàn thân sâu.

**Bảng tham chiếu tiến bộ:**

| Mức | Rounds hoàn thành (15 phút) | AMRAP Score | Đánh giá |
| :--- | :--- | :--- | :--- |
| Cần cải thiện | < 2 rounds | < 116 | Tập Routine RT-05/RT-07 trước |
| Đạt yêu cầu | 2 – 3 rounds | 116 – 174 | Thể lực trung bình |
| Tốt | 4 – 5 rounds | 232 – 290 | Thể lực tốt, toàn diện |
| Xuất sắc | 6+ rounds | 348+ | Thể lực vượt trội |

---

### AMRAP 05: Athletic Power Benchmark
> **Mục tiêu:** Benchmark cho người tập luyện đều đặn — đo công suất vận động và sức bền dưới áp lực rep cao. Đây là bài test "vận động viên" của hệ thống.  
> **Chỉ số khuyến nghị:** BMI 18.5 – 25.0 | Mức độ: Active – Advanced  
> **Time Cap:** 15 phút

* **Khởi động (3 phút):** Khởi động tích cực, nâng nhịp tim trước khi bắt đầu.
* **Circuit (lặp liên tục trong 15 phút):**

| # | Bài tập | Reps / Round | Ghi chú |
| :--- | :--- | :--- | :--- |
| 1 | **Push-up** | 12 reps | Biên độ đầy đủ, ngực chạm sàn |
| 2 | **Deep Squat** | 15 reps | Góc gối $\le 95^\circ$ |
| 3 | **Lunge đổi chân** | 14 reps *(7/chân)* | Nhịp nhanh, form vững |
| 4 | **Sit-up** | 15 reps | Nhanh nhưng siết bụng đủ |
| 5 | **Jumping Jack tốc độ** | 30 reps | Biên độ tối đa |

* **Tổng rep / Round:** 86 rep
* **Hạ nhiệt (3 phút):** Giãn cơ sâu toàn thân, hít thở phục hồi.

**Bảng tham chiếu tiến bộ:**

| Mức | Rounds hoàn thành (15 phút) | AMRAP Score | Đánh giá |
| :--- | :--- | :--- | :--- |
| Cần cải thiện | < 2 rounds | < 172 | Tập RT-08 để tăng công suất |
| Đạt yêu cầu | 2 – 3 rounds | 172 – 258 | Thể lực vận động viên cơ bản |
| Tốt | 3 – 4 rounds | 258 – 344 | Thể lực cao, sức bền tốt |
| Xuất sắc | 5+ rounds | 430+ | Elite — khả năng vận động đỉnh cao |

---

### AMRAP 06: Max Output Sprint
> **Mục tiêu:** Bài test cường độ cao nhất — đo giới hạn vận động cực đại trong thời gian ngắn. Time Cap ngắn (10 phút) buộc người dùng phải đẩy công suất tối đa ngay từ đầu.  
> **Chỉ số khuyến nghị:** BMI 18.5 – 25.0 | Mức độ: Advanced (Cảnh báo: Không khuyến nghị cho người bệnh tim mạch)  
> **Time Cap:** 10 phút

* **Khởi động (3 phút):** Khởi động tích cực + 30 giây sprint jumping jack để nâng nhịp tim.
* **Circuit (lặp liên tục trong 10 phút):**

| # | Bài tập | Reps / Round | Ghi chú |
| :--- | :--- | :--- | :--- |
| 1 | **Jumping Jack tốc độ tối đa** | 30 reps | Mở chân rộng, tay qua đầu |
| 2 | **Push-up bùng nổ** | 10 reps | Nhanh nhưng full ROM |
| 3 | **Squat nhanh** | 15 reps | Đùi song song sàn, nhịp nhanh |
| 4 | **Sit-up nhanh** | 10 reps | Siết bụng, không giật cổ |

* **Tổng rep / Round:** 65 rep
* **Hạ nhiệt (3 phút):** Đi bộ chậm, hít thở sâu, điều hòa nhịp tim.

**Bảng tham chiếu tiến bộ:**

| Mức | Rounds hoàn thành (10 phút) | AMRAP Score | Đánh giá |
| :--- | :--- | :--- | :--- |
| Cần cải thiện | < 2 rounds | < 130 | Cần xây nền tảng sức bền trước |
| Đạt yêu cầu | 2 – 3 rounds | 130 – 195 | Công suất bùng nổ ổn |
| Tốt | 3 – 4 rounds | 195 – 260 | Sức bền trong cường độ cao tốt |
| Xuất sắc | 5+ rounds | 325+ | Giới hạn vận động cực đại — elite level |

---

## Chiến Lược Sử Dụng AMRAP Trong App

### Khi Nào Gợi Ý AMRAP Cho Người Dùng?

1. **Lần đầu sử dụng app (Onboarding):**
   * Gợi ý **AM-02 (Foundation Benchmark)** như bài test đầu vào để xác định fitness baseline.
   * Kết quả AM-02 kết hợp BMI → Gợi ý bộ Routine truyền thống phù hợp.

2. **Đánh giá tiến bộ định kỳ (mỗi 4 – 8 tuần):**
   * Nhắc nhở người dùng thực hiện lại cùng bài AMRAP đã test trước đó.
   * So sánh AMRAP Score qua các lần để trực quan hóa sự tiến bộ trên biểu đồ.

3. **Khi người dùng muốn thử thách bản thân:**
   * Hiển thị AMRAP như tính năng "Challenge" — người dùng chọn mức AMRAP phù hợp và cố gắng phá kỷ lục cá nhân (Personal Best / PB).

### Lưu Trữ Kết Quả AMRAP

Mỗi lần thực hiện AMRAP cần lưu:
```kotlin
data class AmrapResult(
    val amrapId: String,          // "am_01", "am_02", ...
    val completedAt: Instant,     // Thời điểm hoàn thành
    val timeCaptSeconds: Int,     // Giới hạn thời gian (giây)
    val completedRounds: Int,     // Số round hoàn chỉnh
    val extraReps: Int,           // Rep thừa của round chưa hoàn thành
    val totalScore: Int,          // = completedRounds * repsPerRound + extraReps
    val avgFormScore: Float,      // Form Score trung bình toàn bài (0-100)
    val roundDetails: List<RoundDetail>  // Chi tiết từng round
)

data class RoundDetail(
    val roundIndex: Int,
    val exercises: List<ExerciseResult>  // Kết quả từng bài tập trong round
)
```

---
---

## Đặc Tả Tích Hợp Kỹ Thuật Vào Mã Nguồn Android

### Ánh Xạ Schema Room Cho Cả 2 Loại

#### Structured Routines (Phần A):
```kotlin
// Table: routines
RoutineEntity(id = "rt_01_joint_safe", name = "Joint-Safe Mobility & Tone", type = "STRUCTURED")

// Table: routine_steps
RoutineStepEntity(
    routineId = "rt_01_joint_safe",
    orderIndex = 0,
    exercise = Exercise.SQUAT,
    targetReps = 8,
    restIntervalSeconds = 35
)
```

#### AMRAP (Phần B):
```kotlin
// Table: amrap_configs
AmrapConfigEntity(
    id = "am_01_gentle_starter",
    name = "Gentle Starter Test",
    timeCaptSeconds = 480,  // 8 phút
    warmUpSeconds = 120,
    coolDownSeconds = 120,
    minBmi = null,
    maxBmi = null,     // hoặc 50.0
    difficultyLevel = "BEGINNER"
)

// Table: amrap_circuit_steps
AmrapCircuitStepEntity(
    amrapId = "am_01_gentle_starter",
    orderIndex = 0,
    exercise = Exercise.SQUAT,
    repsPerRound = 5
    // Không có restIntervalSeconds — AMRAP tự quyết định nghỉ
)
```

### Cập Nhật `BundledRoutines.kt`

```kotlin
object BundledRoutines {
    // --- Phần A: Structured Routines ---
    val structuredRoutines: List<Pair<RoutineEntity, List<RoutineStepEntity>>> = listOf(
        // RT-01: Joint-Safe Mobility & Tone (BMI >= 30)
        RoutineEntity("rt_01", "Joint-Safe Mobility & Tone") to listOf(
            RoutineStepEntity("rt_01", 0, Exercise.SQUAT, 8, 35),
            RoutineStepEntity("rt_01", 1, Exercise.PUSH_UP, 6, 35),
            RoutineStepEntity("rt_01", 2, Exercise.JUMPING_JACK, 15, 45),
            RoutineStepEntity("rt_01", 3, Exercise.SQUAT, 8, 35),
            RoutineStepEntity("rt_01", 4, Exercise.PUSH_UP, 6, 35),
            RoutineStepEntity("rt_01", 5, Exercise.JUMPING_JACK, 15, 0)
        ),
        // RT-03: Lean Muscle Upper & Core (BMI < 18.5)
        RoutineEntity("rt_03", "Lean Muscle Upper & Core") to listOf(
            RoutineStepEntity("rt_03", 0, Exercise.PUSH_UP, 10, 45),
            RoutineStepEntity("rt_03", 1, Exercise.SIT_UP, 12, 45),
            RoutineStepEntity("rt_03", 2, Exercise.PUSH_UP, 8, 60),
            RoutineStepEntity("rt_03", 3, Exercise.PUSH_UP, 10, 45),
            RoutineStepEntity("rt_03", 4, Exercise.SIT_UP, 12, 0)
        ),
        // RT-07: Full Body Basics Plus (BMI 18.5 - 24.9)
        RoutineEntity("rt_07", "Full Body Basics Plus") to listOf(
            RoutineStepEntity("rt_07", 0, Exercise.SQUAT, 12, 25),
            RoutineStepEntity("rt_07", 1, Exercise.PUSH_UP, 10, 25),
            RoutineStepEntity("rt_07", 2, Exercise.SIT_UP, 12, 25),
            RoutineStepEntity("rt_07", 3, Exercise.JUMPING_JACK, 25, 45)
        ),
        // RT-09: High-Intensity Metabolic HIIT (Advanced)
        RoutineEntity("rt_09", "High-Intensity Metabolic HIIT") to listOf(
            RoutineStepEntity("rt_09", 0, Exercise.JUMPING_JACK, 35, 15),
            RoutineStepEntity("rt_09", 1, Exercise.PUSH_UP, 12, 15),
            RoutineStepEntity("rt_09", 2, Exercise.SQUAT, 15, 15),
            RoutineStepEntity("rt_09", 3, Exercise.SIT_UP, 12, 35)
        ),
        // RT-10: Express Desk-Worker Reset (All BMI)
        RoutineEntity("rt_10", "Express Desk-Worker Reset") to listOf(
            RoutineStepEntity("rt_10", 0, Exercise.SQUAT, 10, 25),
            RoutineStepEntity("rt_10", 1, Exercise.JUMPING_JACK, 20, 25),
            RoutineStepEntity("rt_10", 2, Exercise.LUNGE, 8, 35),
            RoutineStepEntity("rt_10", 3, Exercise.SQUAT, 10, 25),
            RoutineStepEntity("rt_10", 4, Exercise.SIT_UP, 10, 0)
        )
        // ... (Đầy đủ 10 routines được cấu hình sẵn trong database)
    )

    // --- Phần B: AMRAP Configs ---
    val amrapConfigs: List<Pair<AmrapConfigEntity, List<AmrapCircuitStepEntity>>> = listOf(
        // AM-01: Gentle Starter Test (BMI >= 28, Beginner)
        AmrapConfigEntity("am_01", "Gentle Starter Test", 480, 120, 120, 28.0f, null, "BEGINNER") to listOf(
            AmrapCircuitStepEntity("am_01", 0, Exercise.SQUAT, 5),
            AmrapCircuitStepEntity("am_01", 1, Exercise.PUSH_UP, 3),
            AmrapCircuitStepEntity("am_01", 2, Exercise.JUMPING_JACK, 10)
        ),
        // AM-02: Foundation Benchmark (All BMI, Beginner-Moderate)
        AmrapConfigEntity("am_02", "Foundation Benchmark", 600, 120, 120, null, null, "MODERATE") to listOf(
            AmrapCircuitStepEntity("am_02", 0, Exercise.PUSH_UP, 5),
            AmrapCircuitStepEntity("am_02", 1, Exercise.SQUAT, 10),
            AmrapCircuitStepEntity("am_02", 2, Exercise.SIT_UP, 10)
        ),
        // AM-04: Full-Body Capacity Test (BMI 18.5-27, Moderate-Active)
        AmrapConfigEntity("am_04", "Full-Body Capacity Test", 900, 180, 180, null, 27.0f, "ACTIVE") to listOf(
            AmrapCircuitStepEntity("am_04", 0, Exercise.SQUAT, 10),
            AmrapCircuitStepEntity("am_04", 1, Exercise.PUSH_UP, 8),
            AmrapCircuitStepEntity("am_04", 2, Exercise.LUNGE, 10),
            AmrapCircuitStepEntity("am_04", 3, Exercise.SIT_UP, 10),
            AmrapCircuitStepEntity("am_04", 4, Exercise.JUMPING_JACK, 20)
        ),
        // AM-06: Max Output Sprint (Advanced)
        AmrapConfigEntity("am_06", "Max Output Sprint", 600, 180, 180, null, 25.0f, "ADVANCED") to listOf(
            AmrapCircuitStepEntity("am_06", 0, Exercise.JUMPING_JACK, 30),
            AmrapCircuitStepEntity("am_06", 1, Exercise.PUSH_UP, 10),
            AmrapCircuitStepEntity("am_06", 2, Exercise.SQUAT, 15),
            AmrapCircuitStepEntity("am_06", 3, Exercise.SIT_UP, 10)
        )
        // ... (Đầy đủ 6 AMRAP configs)
    )
}
```

---

## Hướng Dẫn Tư Vấn Người Dùng Trong Giao Diện (UI Recommendation Logic)

Khi người dùng nhập **Chiều cao** và **Cân nặng** trong màn hình cài đặt / hồ sơ cá nhân:

### Bước 1: Tính toán BMI
```kotlin
val bmi = weightKg / ((heightCm / 100f) * (heightCm / 100f))
```

### Bước 2: Gợi ý bài test AMRAP đầu vào
* **Tất cả người dùng mới** → Gợi ý **AM-02 (Foundation Benchmark)** để xác định fitness baseline.
* `bmi >= 30.0` → Gợi ý **AM-01 (Gentle Starter Test)** thay cho AM-02 với cảnh báo: *"Bài test nhẹ nhàng để đánh giá thể lực an toàn"*.

### Bước 3: Gợi ý Routine truyền thống phù hợp
* `bmi >= 30.0` &rarr; Tự động ghim tag **"Khuyên dùng"** vào **RT-01 (Joint-Safe Mobility)** và **RT-02**. Kèm cảnh báo an toàn: *"Ưu tiên bài tập Low-impact để bảo vệ khớp gối"*.
* `bmi in 25.0..29.9` &rarr; Gợi ý **RT-05 (Steady Metabolic Burner)** hoặc **RT-06**.
* `bmi in 18.5..24.9` &rarr; Gợi ý **RT-07 (Full Body Basics Plus)**, **RT-08**, hoặc **RT-09 (HIIT)** tùy mức độ thể lực.
* `bmi < 18.5` &rarr; Gợi ý **RT-03 (Lean Muscle Upper)** và **RT-04 (Muscle Builder)**.

### Bước 4: Tracking tiến bộ
* Sau mỗi 4 – 8 tuần, gợi ý người dùng thực hiện lại cùng bài AMRAP.
* Hiển thị biểu đồ so sánh AMRAP Score qua các lần test.
* Nếu AMRAP Score tăng đáng kể → Gợi ý nâng cấp lên Routine/AMRAP khó hơn.
