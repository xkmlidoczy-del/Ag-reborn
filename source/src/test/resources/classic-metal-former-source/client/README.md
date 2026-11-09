# Portable original Metal Former client witnesses

The `academy/` files are exact original AcademyCraft 1.0.7 source/XML/language/event bytes. The `lambda/` files are exact LambdaLib 1.2.3 source bytes. Their original headers remain intact; AcademyCraft sources are GPLv3 and LambdaLib sources are MIT, as recorded in the repository NOTICE. `native-cube.json` is the actual cached Minecraft 1.21.1 full cube model inherited by the staged block models.

`manifest.json` pins every copied source file and all 22 existing original production media assets. It includes 21 PNG dimensions, raw decoded RGBA pixel hashes and alpha bounds, XML-derived fractional geometry, all 24 legacy face assignments, and the original work-audio stream metadata. The Java test pins the whole manifest SHA-256 as a literal before reading any hash list. Verification never regenerates this fixture or needs the reference/staging directories.

The portable Java check validates promoted source, native models, resources, slot/menu/page bindings, source sound lifecycle, and exact name aliases in the four existing locales. The test decodes the original source PNG scanlines directly: the six block textures are 16-bit RGBA and the GUI textures are 8-bit RGBA. Raw-channel checks avoid ImageIO's ICC color conversion, while leaving the media untouched.

This is static source/resource and pure-JVM verification. It does not claim native audiovisual observation or playback parity.
