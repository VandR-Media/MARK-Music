# MARK Cosmic — постановка хореографии v1 (план контента, НЕ готовые клипы)

Цель: 20 визуально разных танцевальных направлений по 12 завершённых 3D-движений.
**Ни одна запись ниже сама по себе не является готовой скелетной анимацией.** Для каждого
элемента необходимо создать ключевые кадры, корневое движение, постановку стоп,
контактные метки, начало/конец нейтральной фразы и превью на одобренной модели Марка.

## Утверждённый визуальный ориентир

- Персонаж — мультяшный мальчик с узнаваемыми пропорциями и чертами из предоставленных пользователем фотографий, а не механический робот.
- Образ MARK Cosmic: тёмно-синее худи с волной, красная кепка M, наушники с подсветкой M, свободные штаны, светящиеся кроссовки; большая космическая сцена.
- Монолитная skinned-модель со скелетом и мимикой (глаза/брови/рот), естественные локти, колени и стопы. Вращение целого «картонного» рендера запрещено.
- Фото ребёнка — только авторизованный персональный референс, не включать фотографии в публичный код или телеметрию.

## Каталог движений (12 элементов на стиль)

Каждое название — самостоятельный **замысел движения**, а не переименованная комбинация из трёх синусов.

| Стиль | 12 движений, которые нужно анимировать |
| --- | --- |
| Hip-hop Groove | bounce two-step; shoulder rock; heel-toe groove; low-side step; chest pulse; hand roll; cross-body point; forward-back groove; body wave; diagonal foot slide; double bounce; final accent pose |
| Old School | classic running man; side bop; knee scoop; heel bounce; crossover step; shoulder shrug; clap turn; robot-to-groove; back step; pocket groove; diagonal heel kick; old-school finishing pose |
| Popping | chest hit; elbow hit; wrist hit; neck isolation; dime stop; arm wave; chest wave; slow ticking; leg pop; side glide; double hit combo; slow freeze |
| Locking | double point; wrist roll; stop-and-lock; scooby step; funky kick; reach-and-freeze; knee lift; shoulder roll; lock-and-turn; cross-point; arm circle; strong lock finish |
| Breaking Footwork | two-step top rock; cross top rock; Indian step; knee rock; six-step simplified; side kick-out; CC-inspired leg sweep; step-over; shoulder drop; low footwork circle; grounded freeze; rise-to-toprock |
| House | jack groove; pas de bourrée; skating step; heel-toe run; salsa house step; side jack; cross-foot scissors; pivot turn; stomp accent; loose shuffle; travel step; jack-and-turn |
| Shuffle | running man; T-step; double running man; heel swivel; cross shuffle; kick slide; backward running man; diagonal T-step; spin shuffle; toe tap; fast kick step; shuffle freeze |
| Electro | angular arm sweep; diagonal wrist wave; sharp chest hit; box-step; symmetrical arm hit; elbow wave; forward glide; elbow cross; full-body pulse; pivot pose; fast wrist switch; robot-electro finish |
| Waacking | single arm circle; double arm circle; overhead whip; shoulder roll; pose break; side whirl; cross-front sweep; head-and-arm accent; rapid alternate arc; spin-and-pose; mirrored arm circle; dramatic finish |
| Tutting | square hand frame; wrist hinge; diagonal elbow frame; rectangle change; hand box unfold; cross-frame; finger line; shoulder-level angles; overhead square; pyramid shape; rotating frame; final geometric lock |
| Dancehall | side bounce; heel rock; travel step; alternating shoulder; knees-in bounce; diagonal hip step; bounce-and-point; turn-and-rock; double step; arms up groove; rhythmic stamp; positive pose |
| Afro Dance | syncopated side step; shoulder pulse; heel lift; body roll; low knee bounce; quick foot switch; diagonal reach; chest-and-hip groove; small traveling loop; ankle bounce; off-beat clap; rising finish |
| Amapiano | relaxed foot pivot; low log-drum stomp; heel slide; shoulder sink; knee cross; double ankle tick; small bounce spin; foot tap-and-freeze; groove walk; half-turn bounce; low-to-high accent; mellow finish |
| Reggaeton | dembow bounce; side tap; heel step; shoulder syncopation; diagonal foot step; forward bounce; reverse step; rhythm hip isolation; upper-body push; double tap; half turn; accent stop |
| Salsa | forward basic; back basic; side basic; right turn; left turn; cross step; open break solo; shoulder shine; syncopated tap; diagonal rock; two-count travel; salsa finishing pose |
| Bachata | side triple-tap; reverse triple-tap; hip accent; smooth weight transfer; front-back basic; small turn; diagonal tap; shoulder soft roll; cross-body step; double tap; easy sway; relaxed pose |
| Disco | disco point; alternating point; hustle step; double clap; side kick; hand overhead; pivot pose; disco walk; mirror-ball reach; twist step; spin point; big final pose |
| Funk | knee dip; heel stomp; funky chicken-inspired step; shoulder snap; bass accent; heel pivot; side groove; arm staccato; body roll; knee turn; walk-and-hit; confident finish |
| Jazz Swing | Charleston; side kick; toe-heel swivel; step ball change; swing step; Suzie-Q-inspired; cross kick; double step; playful jump; shoulder shimmy; pivot; jazz hands finish |
| K-pop | synchronized eight-count; sharp hand frame; heel step; diagonal body angle; head nod-and-pose; cross-arms; shoulder combo; foot cross; wrist detail; clean half-turn; pop accent; center finish |

## Необходимый формат каждого клипа

- ID и стиль, 4 или 8 долей, уровень энергии, BPM-диапазон, наличие сложных вращений.
- Ключевые кадры 3D-скелета + морфы лица; pose-at-entry и pose-at-exit.
- Отдельные метки контактов левой/правой стопы, компенсация корня/бедра (IK).
- Метки событий: удар/акцент, моргание, свободная рука, разворот и «безопасная» точка стыка.
- Проверка 60/90/120 Гц: анимация идёт по музыкальной временной шкале, не по количеству кадров.
- Не допускаются несвязанные покадровые случайные изменения движений.

## Правило выбора стилей

1. Если достоверный жанровый тег есть в метаданных трека — учитывать его наряду с BPM/динамикой.
2. Если тега нет, предлагать **похожую пластику по темпу и энергии**, не объявлять, что жанр «распознан».
3. 4 такта до переключения: подтвердить новую устойчивую характеристику.
4. При переключении: завершить текущую фразу → нейтральная поза со стопами на полу → мягко войти в новую фразу.
5. Не переключать стили на каждом ударе, каждые секунды или в тишине.

## NPC при паузе

- Сразу снижать интенсивность танца и возвращать Марка в стойку за 0.4–0.7 с.
- Постоянные малозаметные действия: дыхание, микродвижения веса, моргание, спокойный взгляд.
- Редкие приветствия: махнуть рукой, коротко кивнуть, показать большой палец, знак V, улыбнуться, поправить кепку или наушники.
- Интервал между приветствиями нерегулярный, ориентировочно 12–26 с; не повторять один жест три раза подряд.
- На паузе Марк не подпрыгивает и не «пляшет от тишины».

## Сдача по этапам

1. Утвердить 3 ракурса riggable 3D-персонажа MARK Cosmic и проверить сходство.
2. Собрать 3 разных настоящих стиля × 12 клипов с переходом и NPC. Снять превью.
3. Развивать библиотеку блоками до 20 × 12; отдельно проверять узнаваемость пластики.
4. Подключить автоматический подбор через анализ PCM и доступные теги трека.
5. Профилировать Huawei Pura 80 Ultra: FPS, задержку аудио/анимации, температуру, память.
6. Релизный подписанный APK — **только после** реального рендера и утверждения качества.

Эта спецификация не доказывает реализацию 240 клипов. Она задаёт критерии их последующего производства и проверки.
