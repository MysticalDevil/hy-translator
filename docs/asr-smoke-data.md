# ASR Smoke Data

The app uses sherpa-onnx streaming Zipformer for bilingual Chinese/English
ASR. Use small deterministic WAV files for smoke tests, and reserve large
standard corpora for manual or scheduled regression runs.

## Smoke Samples

- Default smoke source:
  `csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20`
  `test_wavs/*.wav` on Hugging Face:
  <https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20/tree/main/test_wavs>.
- Download command:

```bash
scripts/download-asr-smoke-audio.sh
```

The files are stored in `build/asr-smoke-audio/` and are not committed.

## Standard Regression Corpora

- English: LibriSpeech `test-clean` from OpenSLR SLR12:
  <https://www.openslr.org/12>.
- Mandarin: AISHELL-1 from OpenSLR SLR33:
  <https://www.openslr.org/33>.
- Broad crowd-sourced coverage: Mozilla Common Voice clips:
  <https://commonvoice.mozilla.org/datasets>.

These datasets are too large for normal debug builds. Keep them outside the
repository and use them for scheduled ASR evaluation.
