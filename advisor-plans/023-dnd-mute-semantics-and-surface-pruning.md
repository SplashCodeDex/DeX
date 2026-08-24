# Plan 023: dnd-mute-semantics-and-surface-pruning

Priority: P1. Effort: M. Depends on: 022.

## User directives (2026-08-24)

1. DND means MUTE ALERTS (incoming-file, clipboard, pairing-request notifications) —
   never refuse. A user wanting total silence closes the app entirely.
2. UPnP is core behavior, always ON, no settings surface. (Executor note: the only
   counter-argument is security posture for users who don't want WAN ports opened;
   product direction says WAN reachability IS the product. No doubt blocking.)
3. "Connect ADB" targets ONE selected phone (power-user tool), not every device.
4. Remove the Auto-Connect ADB Hotspot feature entirely.
5. Download Location change must not be understood as touching transfer history
   (answered: files are never moved/deleted; History browses the current folder).
6. Remove the "DeX Project" (GitHub) item.

## Changes

- `ShareRoutes`: removed the DND → Forbidden refusal for trusted inbound transfers.
- `WebSocketRoutes`: pair-request always surfaces the prompt/mints a PIN; DND no longer
  drops it (pairing is interactive consent, not a passive alert).
- `DesktopPlatformEngine`: gained optional `DeviceConfig`; `showSystemNotification` is
  the single mute point — suppressed under DND with a log line.
- `DesktopUpnpService`: reverted to unconditional configure-on-start; pref plumbing gone.
- `DeviceConfig`: `upnp_enabled` and `auto_adb_hotspot_enabled` keys/flows/setters/init
  lines removed (DataStore ignores the stale keys of existing installs); DND doc updated.
- `SettingsPanel`: UPnP row and Auto-Connect row removed; Connect ADB opens an
  `AdbDevicePickerDialog` (discovered devices, name + IP, empty-state text); "DeX Project"
  row removed, section renamed "Maintenance"; unused wifi import dropped.
- Deleted `composeApp/.../desktop/AutoAdbHotspotService.kt`; removed its main.kt wiring.

## STOP conditions

- Never touch Archived_Legacy_WPF references to DND (C# parity is historical only).
- Keep the shutdown-time `releaseMappedPorts()` — always-on UPnP still cleans up on quit.

## Verification

- gradlew build + spotlessCheck + all desktopTest suites green (ShareRoutesTest included).
- Runtime smoke boot clean with listeners up.
