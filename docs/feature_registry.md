# DeX Feature Registry (Regression Firewall)

This document tracks every core feature from the legacy dual-stack apps (Android / WPF). It acts as our "Regression Firewall" during the Compose Multiplatform (CMP) migration. No phase is considered complete until all relevant features are marked as Verified.

### Status Key:
- ⏳ **Not Started**: Legacy functionality exists, but CMP porting hasn't begun.
- 🏗️ **In Progress**: Actively being ported to KMP `commonMain` or platform targets.
- 🔄 **Migrated (Needs Test)**: Code is ported, awaiting A/B wire testing or UI golden test.
- ✅ **Verified**: Fully functional on CMP with zero regression.

---

## 1. Network & Discovery (Phase 1)
| ID | Feature | Origin | Status | Verification Method |
|---|---|---|---|---|
| NET-01 | UDP Multicast Discovery | Both | ✅ Verified | Integration (CMP ↔ Legacy) |
| NET-02 | TCP / HTTP Wire Protocol | Both | ✅ Verified | Unit / Wire comparison |
| NET-03 | WebSocket Control Channel | Both | ✅ Verified | Integration (CMP ↔ Legacy) |
| NET-04 | mDNS / NSD Service Advertising | Both | ✅ Verified | LAN packet capture |
| NET-05 | Auto-Pairing (Identity Hash) | Both | ✅ Verified | Integration Test |
| NET-06 | Manual PIN Pairing | Both | ⏳ Not Started | Integration Test |
| NET-07 | Stale Node Cleanup / Keep-Alive | Both | ✅ Verified | Unit / Manual Timeout |

## 2. File Transfer
| ID | Feature | Origin | Status | Verification Method |
|---|---|---|---|---|
| XFR-01 | Send Single File | Both | ⏳ Not Started | Integration Test |
| XFR-02 | Send Batch / Folder | Both | ⏳ Not Started | Integration Test |
| XFR-03 | Receive File | Both | ⏳ Not Started | Integration Test |
| XFR-04 | Transfer Progress / Speed Reporting | Both | ⏳ Not Started | UI / State observation |
| XFR-05 | Cancel Transfer | Both | ⏳ Not Started | Integration Test |
| XFR-06 | Save to Custom Directory / SAF | Both | ⏳ Not Started | File System Check |

## 3. Platform Capabilities & Integration (Phase 3)
| ID | Feature | Origin | Status | Verification Method |
|---|---|---|---|---|
| CAP-01 | Clipboard Sync (Copy/Paste across devices)| Both | ⏳ Not Started | Manual Test |
| CAP-02 | Media & Volume Control | Both | ⏳ Not Started | Manual Test |
| CAP-03 | Screen Mirroring (Stream Frames) | Android | ⏳ Not Started | Manual Test |
| CAP-04 | Screen Mirroring (View Frames) | Windows | ⏳ Not Started | Manual Test |
| CAP-05 | Auto-start on Boot | Windows | ⏳ Not Started | OS Config |
| CAP-06 | Background Service / System Tray | Both | ⏳ Not Started | OS UI |
| CAP-07 | Live Wallpaper Sync | Both | ⏳ Not Started | Manual Test |

## 4. User Interface (Phase 2)
| ID | Feature | Origin | Status | Verification Method |
|---|---|---|---|---|
| UI-01 | Dynamic Material 3 Colors | Android | ⏳ Not Started | Screenshot Golden |
| UI-02 | Liquid Glass (`backdrop`) Effects | Both | ⏳ Not Started | Visual / Screenshot |
| UI-03 | Floating Pill Nav Bar / Shell | Android | ⏳ Not Started | Visual / Screenshot |
| UI-04 | Main Screen (Device List) | Both | ⏳ Not Started | Visual / Screenshot |
| UI-05 | History Screen | Both | ⏳ Not Started | Visual / Screenshot |
| UI-06 | Settings Screen | Both | ⏳ Not Started | Visual / Screenshot |
| UI-07 | Transfer Floating Dialog | Windows | ⏳ Not Started | Visual / Screenshot |
| UI-08 | Responsive Grid vs Compact Layout | Both | ⏳ Not Started | Window Resize Test |
