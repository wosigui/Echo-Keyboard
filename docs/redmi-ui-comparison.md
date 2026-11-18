# Redmi Note 12 Turbo UI calibration

Reference: four user screenshots, 1080 × 2400. Connected device reports 440 dpi.

Full-width portrait geometry uses screenshot pixels scaled by width / 1080, independently of Android font scaling. Toolbar/candidate row: 121 px; key rows: 163 px each; bottom controls: 112 px; gesture clearance: at least 44 px. Normal keyboard top: y=1471; first key top: approximately y=1602. Key corner radius: 19 px; horizontal key inset: 7 px. Key fills sampled from the supplied JPEG: #323335 and #252628; background #17181A.

Implemented: remove reserved composition row, preserve keyboard position while composing, composition chip above candidate row, 分词 key during composition, candidate accent blue, right expansion arrow, custom bottom row widths, small /英 label, calibrated icon sizing and MIUI gesture clearance. Drawing and hit regions share the same key geometry.

Verification: assembleDebug, testDebugUnitTest and lintDebug passed with Zulu JDK 17. Installed on connected device; normal and composing screenshots saved beside this document under screenshots/redmi-full.png and screenshots/redmi-composing.png. Original default Sogou input method restored after verification.

Remaining visual differences: system speech recognizer owns the voice UI in screenshot 1; its panel has not been recreated. Candidate contents come from the AOSP decoder and do not reproduce Sogou emoji suggestions or ordering. Icons use approximate vector paths and letter shapes depend on the device font. Host app todo dialog and status bar are outside the input method. Consequently this revision is not yet a pixel-identical reproduction of all four references.
