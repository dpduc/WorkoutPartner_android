# Hệ Thống "Before You Start" — Màn Hình Chuẩn Bị Trước Bài Tập

> **Status (2026-09-21): design source, partly superseded.** `workout-partner-v3` (`.scratch/workout-partner-v3/spec.md`) took this document as input and made its own decisions; where they differ, the spec and `CONTEXT.md` win. Differences:
> - **Position Check** (what this document calls Camera Setup): no lighting check, no raise-hand gesture and no voice command — the countdown starts automatically once the body is in frame, the distance is right and the Athlete has held still for 2 s; "Start anyway" appears after 15 s.
> - **Skipping guides:** no "seen 3 times" rule. Form Guides are shown once per Exercise/Variant until seen and are always reviewable from the Overview.
> - **Rounds:** Routines have no rounds ([ADR-0008](adr/0008-ten-routine-catalogue-and-separate-amrap-mode.md)), so "Round 2/3" and "↻ 3 Rounds" do not appear.
> - **Guided Warm-Up (Phase 4)**, per-rep and form-warning audio, haptics, colour-coded borders, animated guides and Vietnamese speech are out of scope for v3 (spec, "Out of Scope"). The 10-second countdown that announces the first Exercise replaces Phase 4.
> - **Thresholds:** Jumping Jack good form is now ≥ 125°; Step Jack has its own thresholds (rep ≥ 75°, form ≥ 110°) instead of Jumping Jack's.
> - **Vocabulary:** this document says "Workout", "Camera Setup" and "Exercise Guide Card"; `CONTEXT.md` calls them Session/Routine, Position Check and Form Guide.
>
> The implementation checklist at the end shows what shipped.

## Tổng Quan

Tài liệu này thiết kế luồng UX **"Before You Start"** — một chuỗi màn hình chuẩn bị hiển thị **trước khi** người dùng bắt đầu bất kỳ bài tập nào (Structured Routine hoặc AMRAP). Hệ thống giải quyết 3 vấn đề:

1. **Đảm bảo user đã warm-up đầy đủ** cho bài tập sắp tới.
2. **Hướng dẫn form chuẩn** của từng động tác trong bài tập.
3. **Truyền đạt thông tin khi user đứng xa camera (1.8m – 2.5m)** — không thể đọc text nhỏ trên điện thoại.

---

## Vấn Đề Cốt Lõi: User Đứng Xa Màn Hình

> **Constraint chính:** Trong suốt quá trình tập, user đứng cách điện thoại **1.8m – 2.5m** để camera bắt được toàn thân. Ở khoảng cách này, trên màn hình điện thoại 6.1" – 6.7":
> - Text size **14sp–16sp** (standard body text) → **Không đọc được**
> - Text size **24sp–32sp** (large title) → **Chỉ đọc được khi cố gắng nhìn**
> - Text size **48sp+** → **Đọc được thoải mái nhưng giới hạn nội dung**
> - **Icon/hình ảnh lớn** → Phương tiện truyền tải thông tin hiệu quả nhất

### Nguyên Tắc Thiết Kế Cho Khoảng Cách Xa

| Nguyên tắc | Giải pháp |
| :--- | :--- |
| **Text tối thiểu khi tập** | Chỉ hiển thị số lớn (rep count, timer), icon trạng thái, thanh progress |
| **Thông tin chi tiết TRƯỚC khi tập** | Mọi hướng dẫn form, lưu ý an toàn phải được trình bày **trước khi** user rời khỏi điện thoại |
| **Audio thay thế text** | Dùng TTS (Text-to-Speech) + hiệu ứng âm thanh để truyền đạt chỉ dẫn khi user đang tập |
| **Tín hiệu thị giác lớn** | Dùng màu sắc full-screen, animation lớn thay vì text nhỏ |
| **Haptic (rung)** | Rung ngắn khi chuyển bài, rung dài khi hết giờ nghỉ |

---

## Luồng UX Tổng Thể: Từ Chọn Bài Tập → Bắt Đầu Tập

```
┌─────────────────────┐
│  Chọn Routine/AMRAP │  ← User duyệt danh sách, đang cầm điện thoại
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  PHASE 1:           │  ← User đang cầm điện thoại, đọc được text
│  Workout Overview   │     Xem tổng quan bài tập + danh sách bài tập
│  (Tay cầm điện      │
│   thoại)            │
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  PHASE 2:           │  ← User đang cầm điện thoại, swipe qua từng bài
│  Exercise Guide     │     Xem form chuẩn + lưu ý từng động tác
│  Cards              │
│  (Tay cầm, swipe)   │
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  PHASE 3:           │  ← User đặt điện thoại xuống, bước vào vị trí
│  Camera Setup +     │     Kiểm tra camera bắt được toàn thân
│  Position Check     │
│  (Đặt ĐT, đứng xa) │
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  PHASE 4:           │  ← Countdown 10 giây + TTS đọc bài tập đầu tiên
│  Guided Warm-Up     │     Audio hướng dẫn khởi động
│  Countdown          │
│  (Đứng xa)          │
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  BẮT ĐẦU TẬP       │  ← Pose Estimation bắt đầu tracking
│  (Workout Active)   │
└─────────────────────┘
```

---

## PHASE 1: Workout Overview (Tổng Quan Bài Tập)

> **Trạng thái user:** Đang cầm điện thoại, đọc được mọi thứ.
> **Mục tiêu:** Cho user biết tổng quan bài tập sắp tới để quyết định có bắt đầu không.

### Thông tin hiển thị:

```
┌──────────────────────────────────────────┐
│  🏋️ Full Body Basics Plus               │
│                                          │
│  ⏱ ~16 phút  │  3 Rounds  │  ⭐ Moderate │
│                                          │
│  ─────────────────────────────────────── │
│                                          │
│  Bài tập trong session:                  │
│                                          │
│  1. 🦵 Squat ───────────── 12 reps      │
│  2. 💪 Push-up ──────────── 10 reps      │
│  3. 🔥 Sit-up ──────────── 12 reps      │
│  4. ⭐ Jumping Jack ─────── 25 reps      │
│     ↻ Lặp lại 3 Rounds                  │
│                                          │
│  ─────────────────────────────────────── │
│                                          │
│  ⚠️ Lưu ý:                              │
│  • Đảm bảo không gian trống 2m x 2m     │
│  • Ánh sáng đủ sáng cho camera          │
│  • Trang phục gọn gàng, không rộng      │
│                                          │
│  ┌──────────────────────────────────┐    │
│  │      ▶ XEM HƯỚNG DẪN FORM       │    │
│  └──────────────────────────────────┘    │
│                                          │
│  ┌──────────────────────────────────┐    │
│  │        ⏭ BỎ QUA, BẮT ĐẦU       │    │
│  └──────────────────────────────────┘    │
└──────────────────────────────────────────┘
```

### Chi tiết thiết kế:

| Thành phần | Mô tả |
| :--- | :--- |
| **Header** | Tên bài tập, icon cường độ |
| **Info Bar** | Thời lượng ước tính, số rounds, mức cường độ |
| **Exercise List** | Danh sách các bài tập kèm emoji icon + số rep, dạng scrollable list |
| **Safety Notes** | Các lưu ý về không gian, ánh sáng, trang phục — chỉ hiển thị lần đầu hoặc khi user chưa quen |
| **CTA Primary** | "Xem Hướng Dẫn Form" → Chuyển sang Phase 2 |
| **CTA Secondary** | "Bỏ qua, Bắt đầu" → Cho user đã quen, nhảy thẳng sang Phase 3 |

### Logic "Bỏ qua":
- **Lần đầu** thực hiện routine → Ẩn nút "Bỏ qua", bắt buộc xem Phase 2.
- **Đã thực hiện ≥ 3 lần** cùng routine → Hiển thị nút "Bỏ qua".
- **User bấm "Bỏ qua"** → Lưu preference, lần sau mặc định nhảy sang Phase 3 nhưng vẫn có nút "Xem lại hướng dẫn".

---

## PHASE 2: Exercise Guide Cards (Hướng Dẫn Form Từng Động Tác)

> **Trạng thái user:** Đang cầm điện thoại, swipe qua từng card.
> **Mục tiêu:** Hướng dẫn chi tiết form chuẩn cho **từng** động tác có trong bài tập.

### Thiết kế Card Hướng Dẫn:

Mỗi bài tập trong routine sẽ có **1 card** hướng dẫn. User swipe ngang (ViewPager2/HorizontalPager) để lướt qua.

```
┌──────────────────────────────────────────┐
│          1/4                             │
│                                          │
│  ┌──────────────────────────────────┐    │
│  │                                  │    │
│  │      [ANIMATION / GIF LOOP]      │    │
│  │                                  │    │
│  │    Hình người thực hiện Squat    │    │
│  │    từ góc nhìn phù hợp camera   │    │
│  │                                  │    │
│  └──────────────────────────────────┘    │
│                                          │
│  🦵 SQUAT                                │
│  "Ngồi xổm"                             │
│                                          │
│  ── FORM CHUẨN ──────────────────────── │
│  ✅ Lưng thẳng, ngực mở               │
│  ✅ Đầu gối hướng ra ngoài theo        │
│     mũi chân                            │
│  ✅ Hạ hông xuống ngang đầu gối        │
│     hoặc thấp hơn                       │
│  ✅ Trọng lượng dồn vào gót chân       │
│                                          │
│  ── LỖI PHỔ BIẾN ───────────────────── │
│  ❌ Chụm gối vào trong (Knee Valgus)   │
│  ❌ Gập lưng quá mức, cong cột sống    │
│  ❌ Nhón gót, trọng tâm đổ về trước   │
│                                          │
│  ── AI SẼ KIỂM TRA ─────────────────── │
│  🤖 Góc đầu gối ≤ 100° = Good Form     │
│  🤖 Góc đầu gối ≤ 130° = Rep đếm      │
│                                          │
│               ● ○ ○ ○                    │
│  ┌──────────────────────────────────┐    │
│  │          TIẾP THEO →             │    │
│  └──────────────────────────────────┘    │
└──────────────────────────────────────────┘
```

### Nội Dung Hướng Dẫn Cho 5 Động Tác Cốt Lõi

---

#### 🦵 SQUAT (Ngồi xổm)

**Vị trí khởi đầu:**
- Đứng thẳng, hai chân rộng bằng vai.
- Mũi chân hơi xoay ra ngoài ~15°–30°.
- Hai tay đặt trước ngực hoặc duỗi thẳng phía trước.

**Thực hiện:**
1. Hít vào, đẩy hông ra sau như ngồi xuống ghế.
2. Hạ thân xuống cho đến khi đùi song song với mặt sàn (hoặc thấp hơn nếu linh hoạt).
3. Thở ra, đẩy gót chân xuống sàn để đứng lên.

**Form chuẩn AI kiểm tra:**
| Tiêu chí | Ngưỡng |
| :--- | :--- |
| Rep được đếm | Góc gối $\le 130°$ |
| Good Form | Góc gối $\le 100°$ (đùi ngang sàn) |

**Lỗi phổ biến & cách khắc phục:**
| Lỗi | Biểu hiện | Khắc phục |
| :--- | :--- | :--- |
| Quarter Squat | Hạ hông chưa đủ thấp, gối chỉ hơi gập | Tưởng tượng ngồi xuống ghế thấp |
| Knee Valgus | Hai đầu gối chụm vào trong | Ý thức đẩy gối ra ngoài theo mũi chân |
| Butt Wink | Hông cuộn vào ở đáy squat, lưng tròn | Chỉ hạ xuống mức giữ được lưng thẳng |
| Nhón gót | Gót chân nhấc khỏi sàn khi hạ xuống | Dồn trọng lượng vào gót, mũi chân giữ thăng bằng |

**Góc camera đề xuất:** Chéo 45° hoặc chính diện. Tránh quay lưng vào camera.

**Animation mô tả:** Loop GIF/Lottie nhìn từ góc chéo 45°, hiển thị rõ:
- Biên độ gối (highlight vùng đầu gối bằng vòng tròn màu)
- Lưng thẳng (đường thẳng dọc cột sống)
- Đáy squat (dừng 0.5 giây ở vị trí thấp nhất)

---

#### 💪 PUSH-UP (Chống đẩy)

**Vị trí khởi đầu:**
- Hai tay đặt rộng hơn vai một chút, thẳng hàng ngang ngực.
- Thân người thẳng từ đầu đến gót chân (plank position).
- Cơ bụng và mông siết nhẹ.

**Thực hiện:**
1. Hít vào, gập khuỷu tay hạ ngực xuống gần sàn.
2. Giữ khuỷu tay góc ~45° so với thân (không xòe ngang 90°).
3. Thở ra, đẩy thân lên về vị trí ban đầu.

**Form chuẩn AI kiểm tra:**
| Tiêu chí | Ngưỡng |
| :--- | :--- |
| Rep được đếm | Góc khuỷu tay $\le 130°$ |
| Good Form | Góc khuỷu tay $\le 90°$ (ngực gần sàn) |

**Lỗi phổ biến & cách khắc phục:**
| Lỗi | Biểu hiện | Khắc phục |
| :--- | :--- | :--- |
| Hạ nửa vời | Khuỷu tay chỉ gập nhẹ, ngực còn xa sàn | Hạ đến khi cảm nhận ngực gần chạm sàn |
| Võng lưng | Hông tụt xuống, lưng dưới cong võng | Siết cơ bụng như đang plank |
| Chổng mông | Hông nhô lên cao hơn vai | Giữ thân thẳng một đường từ đầu đến gót |
| Khuỷu xòe ngang | Khuỷu tay mở ngang 90° | Khép khuỷu tay ~45° so với thân |

**Góc camera đề xuất:** Nghiêng 45° ngang thân để thấy rõ biên độ gập khuỷu tay và đường thẳng thân.

**Animation mô tả:** Loop GIF/Lottie nhìn từ góc nghiêng, hiển thị:
- Vòng highlight góc khuỷu tay
- Đường thẳng dọc thân (từ đầu → gót chân)
- Điểm đáy (ngực gần sàn)

---

#### 🔥 SIT-UP (Gập bụng)

**Vị trí khởi đầu:**
- Nằm ngửa trên sàn, hai gối gập, bàn chân đặt phẳng.
- Hai tay đặt nhẹ bên tai hoặc bắt chéo trước ngực.
- **Không** ghì tay sau gáy (gây áp lực cổ).

**Thực hiện:**
1. Thở ra, siết cơ bụng nâng vai và lưng trên khỏi sàn.
2. Cuộn thân lên cho đến khi ngực gần chạm đùi.
3. Hít vào, từ từ hạ lưng về sàn theo chiều ngược lại.

**Form chuẩn AI kiểm tra:**
| Tiêu chí | Ngưỡng |
| :--- | :--- |
| Rep được đếm | Góc vai-hông-gối $\le 130°$ |
| Good Form | Góc vai-hông-gối $\le 70°$ (thân trên dựng đứng) |

**Lỗi phổ biến & cách khắc phục:**
| Lỗi | Biểu hiện | Khắc phục |
| :--- | :--- | :--- |
| Giật cổ | Dùng tay kéo đầu lên trước, cổ bị gập | Tay đặt nhẹ bên tai, lực từ bụng |
| Dùng đà | Lắc lưng lấy đà để nâng người | Kiểm soát tốc độ, siết bụng liên tục |
| Nâng nửa vời | Chỉ nhấc vai, lưng giữa vẫn dính sàn | Cuộn thân lên từ từ đến khi ngồi thẳng |

**Góc camera đề xuất:** Nghiêng ngang 90° hoặc chéo 45° để thấy rõ biên độ gập thân.

**Animation mô tả:** Loop GIF/Lottie nhìn từ ngang, hiển thị:
- Góc thân trên so với sàn
- Vị trí tay (bên tai)
- Highlight vùng cơ bụng (khu vực lực chính)

---

#### 🏃 LUNGE (Bước chân đơn)

**Vị trí khởi đầu:**
- Đứng thẳng, hai chân khít, tay đặt hông hoặc trước ngực.

**Thực hiện:**
1. Bước một chân dài về phía trước.
2. Hạ hông xuống cho đến khi gối trước vuông góc ($90°$), gối sau gần chạm sàn.
3. Đẩy gót chân trước xuống sàn để đứng trở lại vị trí ban đầu.
4. Đổi chân hoặc thực hiện liên tục (tùy routine).

**Form chuẩn AI kiểm tra:**
| Tiêu chí | Ngưỡng |
| :--- | :--- |
| Rep được đếm | Góc gối trước $\le 130°$ |
| Good Form | Góc gối trước $\le 100°$ (gối vuông góc) |

**Lỗi phổ biến & cách khắc phục:**
| Lỗi | Biểu hiện | Khắc phục |
| :--- | :--- | :--- |
| Gối vượt mũi chân | Gối trước nhô quá mũi chân về phía trước | Bước dài hơn, giữ ống chân thẳng đứng |
| Thân đổ về trước | Vai gập về phía trước, lưng cong | Giữ ngực mở, nhìn thẳng |
| Mất thăng bằng | Lắc lư sang bên khi hạ xuống | Bước chân rộng hơn sang ngang (~ rộng bằng hông) |

**Góc camera đề xuất:** Chéo 45° để thấy rõ góc gối trước và thân trên.

**Animation mô tả:** Loop GIF/Lottie nhìn từ góc chéo, hiển thị:
- Highlight góc gối trước (vòng tròn)
- Đường thẳng ống chân trước (vuông góc sàn)
- Vị trí gối sau (gần chạm sàn)

---

#### ⭐ JUMPING JACK (Nhảy dang tay chân)

**Vị trí khởi đầu:**
- Đứng thẳng, hai chân khít, hai tay xuôi bên thân.

**Thực hiện:**
1. Bật nhảy nhẹ, đồng thời dang chân rộng hơn vai và đưa hai tay lên cao qua đầu.
2. Bật nhảy trở lại, khép chân khít và hạ tay xuống bên thân.
3. Giữ nhịp đều, thở tự nhiên.

**Form chuẩn AI kiểm tra:**
| Tiêu chí | Ngưỡng |
| :--- | :--- |
| Rep được đếm | Góc khuỷu-vai-hông $\ge 90°$ |
| Good Form | Góc khuỷu-vai-hông $\ge 125°$ (tay lên gần/qua đầu; ban đầu 150°, hạ xuống theo dữ liệu thực tế) |

**Lỗi phổ biến & cách khắc phục:**
| Lỗi | Biểu hiện | Khắc phục |
| :--- | :--- | :--- |
| Tay nửa vời | Tay chỉ giơ ngang vai, chưa qua đầu | Vỗ tay trên đầu mỗi rep |
| Chân không mở rộng | Chân chỉ nhấc nhẹ, không dang đủ | Ý thức bước chân rộng hơn vai |
| Mất nhịp | Tay và chân không đồng bộ | Bắt đầu chậm để tìm nhịp, tăng tốc dần |

**Góc camera đề xuất:** Chính diện (0°) — camera nhìn thẳng vào user.

**Biến thể Low-Impact — STEP JACK:** Dành cho BMI $\ge 30$ hoặc người hạn chế khớp:
- Thay bật nhảy bằng bước chân sang mỗi bên.
- Tay vẫn giơ qua đầu (biên độ đầy đủ).
- AI kiểm tra góc vai với ngưỡng riêng của Step Jack: đếm rep ở $\ge 75°$, Good Form ở $\ge 110°$ (là một Exercise Variant, có Personal Best riêng).

**Animation mô tả:** Loop GIF/Lottie nhìn chính diện, hiển thị:
- Biên độ tay (highlight vùng vai khi tay ở đỉnh)
- Biên độ chân (dang rộng rõ ràng)
- Phiên bản Step Jack song song (cho user BMI cao)

---

## PHASE 3: Camera Setup & Position Check

> **Trạng thái user:** Đặt điện thoại xuống giá đỡ/dựa tường, bước lùi ra vị trí tập.
> **Mục tiêu:** Xác nhận camera bắt được toàn bộ cơ thể user và môi trường đủ sáng.

### Giao diện Camera Setup:

```
┌──────────────────────────────────────────┐
│                                          │
│  ┌──────────────────────────────────┐    │
│  │                                  │    │
│  │     CAMERA PREVIEW               │    │
│  │                                  │    │
│  │     ┌────────────────────┐       │    │
│  │     │ ╔══════════════╗   │       │    │
│  │     │ ║   👤 USER    ║   │       │    │
│  │     │ ║  SILHOUETTE  ║   │       │    │
│  │     │ ║              ║   │       │    │
│  │     │ ║              ║   │       │    │
│  │     │ ╚══════════════╝   │       │    │
│  │     └────────────────────┘       │    │
│  │                                  │    │
│  └──────────────────────────────────┘    │
│                                          │
│  ┌──────────────┐  ┌──────────────┐      │
│  │ 📏 KHOẢNG    │  │ 💡 ÁNH SÁNG  │     │
│  │   CÁCH: ✅   │  │      ✅      │      │
│  └──────────────┘  └──────────────┘      │
│                                          │
│  ┌──────────────────────────────────┐    │
│  │ 🦴 NHẬN DIỆN CƠ THỂ: ✅ 33/33  │    │
│  └──────────────────────────────────┘    │
│                                          │
│  🔊 "Đã nhận diện cơ thể.              │
│       Bước vào vị trí tập."             │
│                                          │
│  ┌──────────────────────────────────┐    │
│  │        ✅ SẴN SÀNG               │    │
│  └──────────────────────────────────┘    │
└──────────────────────────────────────────┘
```

### Checklist Tự Động Kiểm Tra:

| Tiêu chí | Cách kiểm tra | Trạng thái |
| :--- | :--- | :--- |
| **Toàn thân trong khung hình** | MediaPipe phát hiện ≥ 28/33 landmarks với confidence ≥ 0.5 | ✅ Đạt / ❌ Thiếu |
| **Khoảng cách phù hợp** | Tỷ lệ chiều cao skeleton so với chiều cao frame nằm trong 40% – 80% | ✅ Đủ xa / ⚠️ Quá gần / ⚠️ Quá xa |
| **Ánh sáng đủ** | Độ sáng trung bình khung hình (mean luminance) ≥ 80/255 | ✅ Đủ sáng / ⚠️ Tối |
| **Ổn định vị trí** | User đứng yên ≥ 2 giây (landmark displacement < threshold) | ✅ Ổn định / ⏳ Đang chờ |

### Silhouette Guide (Khung Hình Bóng):

Hiển thị một **khung viền hình người** (outline) trên camera preview để user biết nên đứng ở đâu:
- **Màu xanh lá** khi user nằm trong khung đúng vị trí.
- **Màu vàng** khi user hơi lệch (vẫn tập được nhưng không tối ưu).
- **Màu đỏ** khi user quá gần/xa hoặc bị cắt khung hình.

### Audio Cues Cho Phase 3:

Vì user đang đứng xa, mọi chỉ dẫn phải qua **TTS hoặc âm thanh**:

| Sự kiện | Audio Cue (TTS) |
| :--- | :--- |
| User vào khung hình | *"Đã nhận diện cơ thể."* |
| User quá gần | *"Hãy lùi ra xa hơn một chút."* |
| User quá xa | *"Hãy tiến lại gần hơn."* |
| Ánh sáng quá tối | *"Ánh sáng hơi tối. Hãy bật thêm đèn nếu có thể."* |
| Tất cả checklist pass | *"Sẵn sàng! Bấm nút trên màn hình hoặc giơ tay phải để bắt đầu."* |

### Gesture "Bắt Đầu" Từ Xa:

Vì user đứng xa không bấm được nút, cung cấp **cách bắt đầu không cần chạm điện thoại**:

**Option A — Raise Hand Gesture:**
- User giơ **tay phải lên cao quá đầu** và giữ 2 giây.
- AI phát hiện góc khuỷu-vai-hông $\ge 160°$ kéo dài 2 giây → Trigger bắt đầu.
- Hiển thị progress ring trên màn hình khi đang giữ.

**Option B — Voice Command (tùy chọn nâng cao):**
- User nói *"Bắt đầu"* hoặc *"Start"*.
- Yêu cầu permission RECORD_AUDIO (có thể phức tạp hơn).

**Option C — Countdown tự động:**
- Sau khi tất cả checklist pass, tự động countdown 10 giây.
- User chỉ cần đứng yên chờ.

> **Khuyến nghị:** Dùng **Option A (Raise Hand) + Option C (Auto Countdown)** làm mặc định. Option B là tính năng nâng cao.

---

## PHASE 4: Guided Warm-Up Countdown

> **Trạng thái user:** Đứng xa, không đọc được text nhỏ.
> **Mục tiêu:** Hướng dẫn warm-up bằng audio + visual lớn, sau đó countdown vào bài tập.

### Luồng Warm-Up:

```
BƯỚC 1: TTS đọc "Khởi động 2 phút. Hãy theo hướng dẫn."
         Màn hình: Số đếm ngược lớn 2:00 (font 120sp+)
         Nền: Gradient xanh dương nhẹ nhàng

BƯỚC 2: TTS đọc "Xoay khớp vai. Mỗi bên 10 vòng."
         Màn hình: Icon vai xoay (animation lớn chiếm 60% màn hình)
         Timer: 30 giây

BƯỚC 3: TTS đọc "Xoay hông. Mỗi bên 10 vòng."
         Màn hình: Icon hông xoay
         Timer: 30 giây

BƯỚC 4: TTS đọc "Nâng gối nhẹ tại chỗ."
         Màn hình: Icon nâng gối
         Timer: 30 giây

BƯỚC 5: TTS đọc "Vươn vai, hít thở sâu."
         Màn hình: Icon hít thở
         Timer: 30 giây

BƯỚC 6: TTS đọc "Khởi động hoàn tất. Chuẩn bị bắt đầu
                  bài tập đầu tiên: Squat, 12 reps."
         Màn hình: Countdown 5...4...3...2...1...GO!
         (Số lớn, chiếm full screen, kèm âm thanh beep)
```

### Nguyên Tắc Warm-Up Theo Loại Bài Tập:

Không phải bài tập nào cũng warm-up giống nhau. Warm-up phải **phù hợp với nhóm cơ** sẽ sử dụng:

| Nhóm cơ chính trong Routine | Warm-Up bắt buộc |
| :--- | :--- |
| **Squat / Lunge** (chân) | Xoay hông, xoay cổ chân, nâng gối, ép dẻo chân |
| **Push-up** (ngực/vai/tay) | Xoay khớp vai, xoay cổ tay, mở ngực |
| **Sit-up** (bụng/lưng) | Cat-Cow stretch, nghiêng lườn |
| **Jumping Jack** (toàn thân/cardio) | Nâng gối, bước nhảy nhẹ tại chỗ |
| **Kết hợp nhiều nhóm** | Xoay khớp toàn thân → Nâng gối → Hít thở |

### Thiết Kế Warm-Up Cho Từng Routine:

Mỗi Routine/AMRAP trong hệ thống đã có phần **"Khởi động"** được thiết kế sẵn (xem [workout-routines-system.md](file:///c:/Users/teflo/Desktop/WORK/Internship/Projects/WorkoutPartner_android/docs/workout-routines-system.md)). Phase 4 biến phần khởi động text đó thành **trải nghiệm guided audio + visual**.

---

## Hệ Thống Audio Trong Quá Trình Tập

Vì user không đọc được text khi đứng xa, **audio là kênh truyền đạt thông tin chính** trong khi tập.

### Bảng Audio Cues:

| Thời điểm | Audio Cue (TTS) | Hiệu ứng âm thanh | Visual (xa) |
| :--- | :--- | :--- | :--- |
| **Bắt đầu set mới** | *"Squat. 12 rep."* | Beep ngắn 1 lần | Tên bài tập font 64sp+, nền đổi màu |
| **Đếm rep** | *"1... 2... 3..."* (mỗi 3 rep) hoặc *"5 rep còn lại"* | Tick nhẹ mỗi rep | Số rep lớn full-screen |
| **Good Form** | *"Tốt lắm!"* (mỗi 5 rep liên tiếp good form) | Chime positive | Flash xanh lá viền màn hình |
| **Bad Form** | *"Hạ sâu hơn"* hoặc *"Giữ lưng thẳng"* | — | Flash vàng viền màn hình |
| **Hoàn thành set** | *"Xong! Nghỉ 30 giây."* | Beep kép | Màn hình chuyển xanh dương, timer nghỉ lớn |
| **Sắp hết nghỉ (5s)** | *"Chuẩn bị. Bài tiếp: Push-up."* | 3 beep ngắn | Countdown 5...4...3... |
| **Hết nghỉ** | *"Bắt đầu! Push-up. 10 rep."* | Beep dài | Nền chuyển màu mới |
| **Hoàn thành round** | *"Hoàn thành Round 1! Còn 2 round."* | Fanfare ngắn | Animation celebration |
| **Lost tracking** | *"Mất tín hiệu. Hãy quay lại vị trí."* | Alert tone | Màn hình đỏ nhấp nháy |
| **Hoàn thành bài** | *"Tuyệt vời! Bài tập hoàn tất."* | Fanfare dài | Celebration screen |

### Thiết Kế TTS Engine:

```kotlin
// Pseudo-code cho TTS Manager
class WorkoutTtsManager(context: Context) {
    private val tts = TextToSpeech(context) { status -> /* init */ }

    // Queue các câu TTS, tự động chờ câu trước nói xong
    fun announce(message: String, priority: Priority = NORMAL)

    // Các template message có sẵn
    fun announceExerciseStart(exercise: Exercise, targetReps: Int)
    fun announceRepCount(currentRep: Int, targetReps: Int)
    fun announceRestStart(restSeconds: Int)
    fun announceRestWarning(secondsLeft: Int)
    fun announceFormFeedback(feedback: FormFeedback)
    fun announceRoundComplete(currentRound: Int, totalRounds: Int)

    enum class Priority { LOW, NORMAL, HIGH, CRITICAL }
}
```

### Ưu tiên Audio (Tránh chồng chéo):

Khi nhiều sự kiện xảy ra cùng lúc, áp dụng priority queue:

1. **CRITICAL:** Lost tracking, lỗi hệ thống → Luôn phát ngay, interrupt mọi audio khác.
2. **HIGH:** Bắt đầu bài mới, hết giờ nghỉ → Phát ngay sau CRITICAL.
3. **NORMAL:** Đếm rep, Form feedback → Phát theo thứ tự queue.
4. **LOW:** Khích lệ (*"Tốt lắm!"*) → Bỏ qua nếu queue đang bận.

---

## Thiết Kế Visual Cho Khoảng Cách Xa (Distance-Friendly UI)

### Nguyên Tắc "Glanceable UI":

Khi user đang tập, họ chỉ **liếc nhanh** màn hình (0.5 – 1 giây). Mọi thông tin quan trọng phải nhận biết được trong thời gian đó.

### Layout Trong Khi Tập (Active Workout Screen):

```
┌──────────────────────────────────────────┐
│                                          │
│              PUSH-UP                     │  ← Tên bài (font 48sp, bold)
│                                          │
│                                          │
│                                          │
│               7/10                       │  ← Rep count (font 120sp)
│                                          │
│                                          │
│         ████████████░░░░                 │  ← Progress bar (chiều rộng)
│                                          │
│  Round 2/3        Form: 85%              │  ← Info phụ (font 28sp)
│                                          │
│  ═══════════════════════════════════════ │
│  🟢🟢🟢🟢🟢🟢🟢⚪⚪⚪                │  ← Rep dots (visual)
│                                          │
└──────────────────────────────────────────┘
```

### Mã Màu Trạng Thái (Nhận Biết Từ Xa):

| Màu nền/viền | Ý nghĩa | Khi nào |
| :--- | :--- | :--- |
| **Xanh dương đậm** | Đang tập bình thường | Trong suốt set |
| **Xanh lá nhấp nháy** | Good form / Rep đạt chuẩn | Flash 0.3s mỗi good rep |
| **Vàng cam** | Cảnh báo form | Khi form < 50% |
| **Xanh dương nhạt** | Đang nghỉ | Giữa các set |
| **Đỏ nhấp nháy** | Mất tracking | Khi user ra khỏi khung hình |
| **Tím / Gradient** | Hoàn thành round | Transition giữa các round |
| **Vàng gold** | Hoàn thành toàn bộ | Celebration screen |

---

## Hệ Thống Animation Hướng Dẫn Động Tác

### Phương Án Tạo Animation:

| Phương án | Ưu điểm | Nhược điểm | Khuyến nghị |
| :--- | :--- | :--- | :--- |
| **A. Lottie Animation (JSON)** | Nhẹ (~50-200KB/file), mượt 60fps, dễ tùy chỉnh màu, có thể tương tác | Cần designer hoặc After Effects | ✅ **Tốt nhất cho long-term** |
| **B. GIF Loop** | Đơn giản, tạo nhanh | Nặng (~500KB-2MB), giới hạn FPS/màu | ⚠️ Dùng cho MVP |
| **C. WebP Animated** | Nhẹ hơn GIF ~30%, hỗ trợ transparency | Ít tool tạo, không tương tác được | ⚠️ Thay thế GIF |
| **D. 3D Model (GLB)** | Xoay được nhiều góc, trải nghiệm premium | Phức tạp, cần engine 3D, nặng | ❌ Quá phức tạp cho MVP |

> **Khuyến nghị:** Bắt đầu với **GIF/WebP cho MVP**, chuyển sang **Lottie** khi sản phẩm ổn định.

### Nguồn Tạo Animation:

1. **Tự tạo:** Quay video người thật → Cắt loop → Chuyển GIF/WebP bằng FFmpeg.
2. **Lottie Files:** Tìm animation bodyweight exercise miễn phí trên [lottiefiles.com](https://lottiefiles.com).
3. **AI Generate:** Dùng AI (video generation) để tạo clip demo động tác, sau đó loop.
4. **Skeleton Animation:** Dùng chính MediaPipe skeleton data → Render 2D stick figure animation. Ưu điểm: nhất quán với những gì AI thực sự "nhìn thấy".

### Danh Sách Asset Animation Cần Thiết:

| Exercise | File name | Góc nhìn | Thời lượng loop | Ghi chú |
| :--- | :--- | :--- | :--- | :--- |
| SQUAT | `anim_squat_guide.webp` | Chéo 45° | 3 – 4s | Highlight vùng gối |
| PUSH_UP | `anim_pushup_guide.webp` | Ngang 45° | 3 – 4s | Highlight khuỷu tay |
| SIT_UP | `anim_situp_guide.webp` | Ngang 90° | 3 – 4s | Highlight góc thân |
| LUNGE | `anim_lunge_guide.webp` | Chéo 45° | 4 – 5s | Highlight gối trước |
| JUMPING_JACK | `anim_jumpingjack_guide.webp` | Chính diện | 2 – 3s | Highlight vai + chân |
| STEP_JACK (biến thể) | `anim_stepjack_guide.webp` | Chính diện | 3 – 4s | Cho BMI cao |

---

## Đặc Tả Kỹ Thuật (Technical Spec)

### Data Model Cho "Before You Start":

```kotlin
// Thông tin hướng dẫn cho mỗi Exercise
data class ExerciseGuide(
    val exercise: Exercise,
    val displayName: String,         // "Squat", "Push-up", ...
    val displayNameVi: String,       // "Ngồi xổm", "Chống đẩy", ...
    val animationRes: Int,           // R.drawable.anim_squat_guide
    val cameraAngle: String,         // "Chéo 45°", "Chính diện", ...

    // Form chuẩn
    val formTips: List<String>,      // ["Lưng thẳng, ngực mở", ...]
    val commonMistakes: List<String>, // ["Chụm gối vào trong", ...]

    // Ngưỡng AI
    val repThreshold: String,        // "Góc gối ≤ 130°"
    val formThreshold: String,       // "Góc gối ≤ 100°"

    // Audio
    val ttsStartCue: String,         // "Squat. 12 rep."
    val ttsFormWarnings: Map<FormIssue, String>  // KNEE_VALGUS → "Đẩy gối ra ngoài"
)

// Warm-up step
data class WarmUpStep(
    val instruction: String,          // "Xoay khớp vai mỗi bên 10 vòng"
    val ttsText: String,              // "Xoay khớp vai. Mỗi bên 10 vòng."
    val durationSeconds: Int,         // 30
    val animationRes: Int?,           // R.drawable.anim_warmup_shoulder (nullable)
    val targetMuscleGroups: List<MuscleGroup>
)

enum class MuscleGroup {
    SHOULDERS, CHEST, CORE, HIPS, KNEES, ANKLES, FULL_BODY
}

// Config cho Camera Setup Check
data class CameraSetupConfig(
    val minLandmarkConfidence: Float = 0.5f,
    val minVisibleLandmarks: Int = 28,
    val skeletonHeightRatioRange: ClosedFloatingPointRange<Float> = 0.40f..0.80f,
    val minLuminance: Int = 80,
    val stabilityDurationMs: Long = 2000,
    val stabilityThresholdPx: Float = 15f
)
```

### Màn Hình & Navigation Flow:

```kotlin
// Sealed class cho các state trong Before You Start flow
sealed class BeforeYouStartState {
    data class Overview(val routine: RoutineEntity, val steps: List<RoutineStepEntity>) : BeforeYouStartState()
    data class ExerciseGuideCards(val guides: List<ExerciseGuide>, val currentIndex: Int) : BeforeYouStartState()
    data class CameraSetup(val checklistStatus: CameraChecklistStatus) : BeforeYouStartState()
    data class GuidedWarmUp(val steps: List<WarmUpStep>, val currentStep: Int, val remainingSeconds: Int) : BeforeYouStartState()
    object ReadyToStart : BeforeYouStartState()
}

data class CameraChecklistStatus(
    val bodyDetected: Boolean = false,
    val distanceOk: Boolean = false,
    val lightingOk: Boolean = false,
    val positionStable: Boolean = false
) {
    val allPassed: Boolean get() = bodyDetected && distanceOk && lightingOk && positionStable
}
```

### Lưu Trữ User Preferences:

```kotlin
// SharedPreferences / DataStore
data class BeforeYouStartPrefs(
    val routineGuideSeenCount: Map<String, Int>,  // routineId → số lần đã xem guide
    val skipGuideEnabled: Boolean,                 // User có muốn bỏ qua Phase 2 không
    val ttsEnabled: Boolean,                       // Bật/tắt TTS
    val ttsLanguage: String,                       // "vi" hoặc "en"
    val ttsSpeed: Float,                           // 0.8 – 1.5
    val gestureStartEnabled: Boolean,              // Bật/tắt gesture "giơ tay bắt đầu"
    val autoCountdownSeconds: Int                  // 10 giây mặc định
)
```

---

## Checklist Implementation Ưu Tiên

### MVP (Version 1.0):

- [x] **Phase 1 — Workout Overview:** Hiển thị tổng quan + danh sách bài tập
- [x] **Phase 2 — Exercise Guide Cards:** Swipeable cards với hình ảnh tĩnh (PNG placeholder) + text hướng dẫn form
- [x] **Phase 3 — Camera Setup:** Checklist tự động (body detection + khoảng cách)
- [x] **Phase 4 — Countdown:** Auto-countdown 10 giây + TTS đọc tên bài tập đầu tiên
- [x] **Audio trong tập:** TTS đọc tên bài tập + "Nghỉ X giây" + "Hoàn thành"
- [x] **Distance-Friendly UI:** Số rep font 120sp, tên bài 48sp, progress bar lớn

### Version 1.1:

- [ ] **Animated GIF/WebP** thay thế hình tĩnh trong Phase 2
- [ ] **Guided Warm-Up** với TTS từng bước
- [ ] **Form Warning Audio:** TTS đọc cảnh báo form cụ thể ("Hạ sâu hơn")
- [ ] **Gesture Start:** Giơ tay phải để bắt đầu từ xa
- [ ] **Rep counting audio:** TTS đếm mỗi 5 rep hoặc "Còn 3 rep"

### Version 2.0:

- [ ] **Lottie Animation** thay thế GIF
- [ ] **Skeleton Animation** render từ MediaPipe data
- [ ] **Haptic feedback** khi đếm rep, chuyển bài
- [ ] **Color-coded screen borders** cho form feedback từ xa
- [ ] **Voice command** "Bắt đầu" / "Tạm dừng"
- [ ] **Multilingual TTS** (Vietnamese + English)
