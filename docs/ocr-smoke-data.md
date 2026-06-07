# OCR Smoke Data

The app uses Paddle Lite with PaddleOCR PP-OCRv5 mobile models. Use small
deterministic text images for smoke tests, and reserve larger OCR benchmarks for
manual or scheduled regression runs.

## Smoke Samples

- Default smoke source: PaddleX/PaddleOCR demo images hosted by Baidu BOS:
  <https://paddle-model-ecology.bj.bcebos.com/paddlex/imgs/demo_image/general_ocr_rec_001.png>
  and
  <https://paddle-model-ecology.bj.bcebos.com/paddlex/imgs/demo_image/general_ocr_002.png>.
- Download command:

```bash
scripts/download-ocr-smoke-images.sh
```

The files are stored in `build/ocr-smoke-images/` and are not committed.

## Standard Regression Corpora

- Scene text detection/recognition: ICDAR 2015 Incidental Scene Text:
  <https://rrc.cvc.uab.es/?ch=4>.
- Large scene text annotations: COCO-Text:
  <https://bgshih.github.io/cocotext/>.
- PaddleOCR dataset index for multilingual and task-specific OCR evaluation:
  <https://github.com/PaddlePaddle/PaddleOCR/blob/main/doc/doc_en/dataset/ocr_datasets_en.md>.

These datasets are too large for normal debug builds. Keep them outside the
repository and use them for scheduled OCR evaluation.
