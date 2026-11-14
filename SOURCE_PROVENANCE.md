# Source snapshot and privacy handling

This snapshot follows the verified local Academy generation reborn / Ag:reborn `0.1.0-m41-highpower.0.81.5-city-a3.63` source. The public a3.61 snapshot remains available under its original tag. The original work is [AcademyCraft by Lambda Innovation](https://github.com/LambdaInnovation/AcademyCraft); the classic reference is tag 1.0.7 at commit `00d19ec0cf538f61c1095c9292f5ee6863db4521`.

This repository starts with the privacy-reviewed a3.61 export. Its build inputs and assets follow the public source layout. Local machine paths were replaced with relative paths where needed. Local fields were removed from the editable train model; the model's geometry and materials were compared before and after. Non-rendering text or EXIF chunks were removed from selected generated PNGs, and their decoded pixels were compared. Some original upstream PNG metadata remains where existing runtime or test checks verify the original upstream file hash; it was checked for local usernames, paths and contact details. See `source/docs/ASSET_METADATA_NOTICE.md`.

The a3.63 update retains the previously sanitized train model, portable scripts and 76 PNGs. The publishable tree was reviewed for machine paths, personal identifiers and credentials. An isolated JDK 21 build from the staged tree matched the local a3.63 production classes and packaged resources, with non-rendering metadata differences in the 76 PNGs. Their decoded pixels match.

The a3.63-mech.1 update adds ACA Mech 0.4.3 sources, runtime assets, editable Blockbench models, test driver and license notices under `source/src/mech`, `source/src/mechQa` and `source/integration/aca-mech`. It also changes the version and compatibility integration files. Editable model texture paths are relative. The module's source and converted assets retain their MIT and CC0 notices; the original model author is credited in `source/integration/aca-mech/NOTICE.md` and `ASSET-LICENSE.md`.

`LICENSE` and `NOTICE` preserve the original GPLv3 text, additional AcademyCraft notices and third-party attributions. The interaction between the upstream additional terms and GPLv3 is not resolved here.
