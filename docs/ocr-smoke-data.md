# OCR Smoke And Regression Data

The app uses Paddle Lite with PaddleOCR PP-OCRv5 mobile models. Use small
deterministic text images for smoke tests, and reserve larger OCR benchmarks for
manual or scheduled regression runs.

## Smoke Samples

- Default smoke source: PaddleX/PaddleOCR demo images hosted by Baidu BOS:
  <https://paddle-model-ecology.bj.bcebos.com/paddlex/imgs/demo_image/general_ocr_rec_001.png>
  and
  <https://paddle-model-ecology.bj.bcebos.com/paddlex/imgs/demo_image/general_ocr_002.png>.
- These images are intentionally small and public. They exercise the Android
  Paddle Lite runtime, PP-OCRv5 asset loading, bitmap decode, detection, crop,
  recognition, and CTC decode path without adding large benchmark data to the
  repository.
- Download command:

```bash
scripts/download-ocr-smoke-images.sh
```

The files are stored in `build/ocr-smoke-images/` and are not committed.
The instrumented smoke test downloads the same samples from androidTest string
resources so download URLs stay out of Kotlin sources.

## Standard Regression Corpora

- Scene text detection and end-to-end OCR: ICDAR 2015 Incidental Scene Text:
  <https://rrc.cvc.uab.es/?ch=4>. This is the primary human-captured scene text
  regression source. Registration is required, so it is not automated by the
  debug smoke script.
- Large scene text annotations: COCO-Text V2:
  <https://bgshih.github.io/cocotext/>. Use this for broader scene diversity in
  scheduled regression only.
- PaddleOCR dataset index for multilingual and task-specific OCR evaluation:
  <https://github.com/PaddlePaddle/PaddleOCR/blob/main/docs/datasets/ocr_datasets.en.md>.
  The index documents PaddleOCR label formats, ICDAR 2015 download/annotation
  handling, and recognition benchmark sources such as MJ, SJ, IIIT, SVT, IC03,
  IC13, IC15, SVTP, and CUTE.

These datasets are too large for normal debug builds. Keep them outside the
repository and use them for scheduled OCR evaluation.

Recommended local layout:

```text
external-data/ocr/
  icdar2015/
    ch4_test_images/
    test_icdar2015_label.txt
  coco-text/
    images/
    annotations/
```

`external-data/` is intentionally not part of the app build. Tests that consume
these corpora should accept a local path and skip with an explicit assumption
when the dataset is absent.
