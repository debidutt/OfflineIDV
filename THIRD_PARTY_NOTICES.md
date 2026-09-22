# Third-party dependency and trust-material notice (M7.3/M7.4 engineering build)

This repository declares the following exact dependencies for the limited Android document-chip integration. This inventory is an engineering notice, not the final legal/open-source compliance pack and not permission to distribute an APK, AAB, AAR, or downstream application.

| Component | Version | Upstream | License finding pending formal review |
| --- | --- | --- | --- |
| JMRTD | 0.8.8 | <https://sourceforge.net/projects/jmrtd/files/passporthostapi/0.8.8/> | LGPL metadata/source-header interpretation and Android distribution obligations remain under legal review. |
| Scuba Smartcards | 0.0.21 | <https://sourceforge.net/projects/scuba/> | LGPL-2.1-or-later finding remains under legal/open-source review. |
| Bouncy Castle Provider | 1.85.2 | <https://www.bouncycastle.org/> | Bouncy Castle permissive license; final copyright, permission, and warranty notice packaging remains required. |
| Bouncy Castle Utility | 1.85 | <https://www.bouncycastle.org/> | Bouncy Castle permissive license; final copyright, permission, and warranty notice packaging remains required. |
| EJBCA cert-cvc | 1.4.13 | <https://github.com/Keyfactor/ejbca-ce/tree/main/modules/cert-cvc> | LGPL-2.1 finding and retained-versus-excluded release decision remain under legal/open-source review. |

M7.4 also bundles Netherlands residence-permit CSCA serial 4, obtained from the official Netherlands PKD certificate export at <https://www.npkd.nl/> and pinned by SHA-256 `0F:D7:E3:BE:92:3B:DB:E8:3B:4E:4B:08:E2:59:74:65:5B:5E:55:F6:61:44:8B:9A:FA:A4:A7:8F:76:0E:D2:BC`. Provenance is recorded in `docs/security/offline-csca-trust-strategy.md`. Redistribution rights and final trust/revocation governance remain subject to legal and PKI review.

The reviewed coordinates, binary/POM/source SHA-256 values, provenance, source commits, risks, and distribution questions are recorded in `docs/security/jmrtd-approval-package.md`. A release must include the complete applicable license texts, copyright notices, source/relinking or replacement materials/instructions required by counsel, modification disclosures, SBOM, exact dependency lock, strict checksum verification, and downstream redistribution terms.

Until those obligations and the security/trust/release/device gates are accepted in writing, Project Atlas M7.3/M7.4 is an internal engineering implementation only.
