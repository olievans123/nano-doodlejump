# Port evidence and outstanding parity work

The user's requirement is a proper port based on full decompilation, not an approximate recreation. A rendered host prototype alone does not satisfy it.

## Original build

- iOS Doodle Jump 1.0, bundle `com.yourcompany.DoodleJump`, ARMv6 Mach-O.
- Executable SHA-256: `68b29c08bcbe25e760dfd92aee05d92d3f3cae394789e7a029c53b210ff0e6d4`.
- IPA SHA-256: `aabb91dc6316496fa4cee29606c88c8c9324d3042c3c0a9a106b00c304a45479`.
- The archived executable has cryptid=0. No decryption or protection bypass was performed.
- Symbols include 205 Objective-C methods; the gameplay is native, not Lua.

## Analysis pipeline

`ghidra/scripts/ExportGame.java` exports the named methods. ARMv6 float-helper imports use custom r0–r3 storage. Read-only __TEXT blocks and DecompileOptions.setRespectReadOnly(true) are necessary to resolve literal-pool stack sizes. Data segments remain writable. One Texture2D CGImage initializer has a decompiler warning; image decoding is replaced by a platform adapter.

`tools/original_oracle.py` runs local ARM methods under Unicorn with bounded instruction counts. It supplies Objective-C dispatch, arrays, CGRect tests, imported arithmetic/libm and sound no-ops. Its OS adapters are a limitation of the comparison, not original iOS execution.

## Recovered routines

| Original method | Address | C destination / status |
|---|---:|---|
| PlatformObject::tick | 0xa094 | objects.c; 4,800 compared ticks |
| MonsterObject::tick | 0xf830 | objects.c; 5,400 compared ticks |
| UfoObject::tick | 0xabec | objects.c; 1,800 compared ticks |
| BonusObject::tick | 0xa290 | objects.c; 4,800 compared ticks |
| ProjectileObject::tick | 0xa9a4 | objects.c; 200 compared ticks |
| JumpAppDelegate::initNewGame | 0x7d5c | start_game/dj_start; layouts compared; UI performs immediate first tick |
| JumpAppDelegate::generateNewObjectAboveScreen | 0x3b1c | generate_platform; 320 platforms and independent bonuses compared |
| JumpAppDelegate::generateNewSceneAboveScreen | 0x3fa0 | generate_scene; all 19 arrangements compared |
| JumpAppDelegate::moveScreenWithOffset | 0x46d4 | move_screen; removal/order audit in progress |
| JumpAppDelegate::checkForCollisions | 0x4f6c | dj_collisions; 144 individual and 150 overlapping/projectile cases compared |
| JumpAppDelegate::mainGameLoop | 0x630c | dj_tick/render; 1,638 compared frames; rendering not compared pixel-for-pixel |
| JumpAppDelegate::accelerometer:didAccelerate: | 0x289c | dj_accelerometer; 1,000 compared double results; independent 100 Hz scheduler |
| EAGLView::touchesEnded:withEvent: | 0xc984 | release-edge shooting |
| JumpAppDelegate::handleTap | 0x4e54 | dj_shoot; comparison pending |
| JumpAppDelegate::startScreenLoop | 0x7ff0 | menu/render; original decorative sprites and bounce recovered |
| JumpAppDelegate::gameDidFinish | 0x4cd0 | finish; original game-over layout, persistence and offline leaderboard adaptation |

## Known details that must not be approximated away

- Original timer: 1/60 second. Tilt filter: 0.06 × incoming acceleration + 0.94 × previous value, stored as a double, sampled initially at 100 Hz.
- X update: float(double(x) + 20 × filteredAccel). Y adds jumpOffset; gravity is float(double(jumpOffset) − 0.24), clamped at −9.
- Normal bounce 9, spring bounce 15.5, scrolling anchor y=230. Collision feet rectangle starts at (x−15,y−27), width28 and height2.
- Platform collision strip: (platformX−25.5,platformY,52,7.5). The original scans all platforms before checking springs. Springs have independent position, movement and lifetime.
- Capture lasts 12 frames for holes, 40 for UFOs. UFO target is y+35. Position is recomputed from the moving target and remaining frames, rather than incrementing by a fixed velocity.
- Capture transforms run after object updates and collision checks in the original render part of mainGameLoop. Scroll-out starts at mainScreenDownOffset=−455 and proceeds by −12 to ≤−975.
- Falling death has a separate accelerating screen animation; stars appear only after a monster collision.
- Draw order: holes, platforms, bonuses, UFOs, monsters, projectiles, player, score overlay.
- Decimal score placement uses half the power-of-two texture width plus5 for each successive digit. It is not a normal proportional-font advance.
- Menu/game transitions use UIView fades: 0.3 seconds out, 0.2 seconds in. The port performs the immediate initNewGame tick then freezes player movement during fade-in. The fade uses the standard ease-in/out cubic curve; UIKit itself is replaced.
- The jumping texture is selected before collisions, so the landing frame uses the prior texture. Main-loop comparisons check both facing/shooting texture and jump texture.
- The unused FallingObject and PointsObject classes are decompiled, but no references constructing them were found in applicationDidFinishLaunching/mainGameLoop. Do not add unused effects just because these classes exist.

## Current limits

The ARM nano app compiles and passes the SDK Thumb-FP gate. A 6,000-tick cross-platform gameplay run plus object motion produces the same `0920542b` digest on the Mac and cross-compiled ARM under QEMU. Texture storage is 1.25 MiB; the application arena is 512 KiB. These are build/allocation results, not measured device performance.

Nano audio is disabled. The existing single-shot SDK audio API cannot reproduce concurrent effects and a stoppable UFO loop; its low-level loader documents a device-reboot issue without particular display activity. Do not wire every sound trigger directly to that API. The host OpenAL adapter loads the original 13 effects, but listening validation remains outstanding.

The SDK wake-lock interface is implemented for LVGL apps; it is not currently used by this GL app. Display idle behaviour, launch/relaunch, control orientation and performance need hardware testing. No firmware/loader modifications are part of this port.

The test build was installed on 1 October 2026. All 30 app/data/icon/pack files matched their staged SHA-256 checksums when read back after flushing the device. Hardware gameplay validation is still pending. No new remote GitHub repository has been created. Do not describe this state as a finished full decompilation port.

`tools/inspect_original.py` reads the actual Mach-O Objective-C ivar tables: 184 distinct entries. The initial `otool -ov` text parse duplicated Texture2D entries and missed five fields; it has been replaced. Existing gameplay offsets were unchanged. The new metadata also correctly identifies ProjectileObject's size and speed fields.

## Hardware-test checkpoint

The local app is `build/doodlejump.hbapp`, 50,272 bytes, SHA-256 `8b469ba25a97fbd3249fe29d01e31c664c2dc99681fad838dd371b4425722d17`. Its image span including BSS is 224,436 bytes. The SDK Thumb-FP gate passes.

`build/nano-test-stage` contains the installed tree. Its four entries are Mario 64, Mario Kart, Angry Birds and Doodle Jump. The first three bundles are preserved byte-for-byte. The freshly read device base matched SHA-256 `91e1f47a24d8946bdb1bc7898d37020f609ddcfb6178e542dbb37f0fafcee05d`. The original pack is backed up on the mini PC at `/home/olipat/nano-dj/device-backup-20261001/AllApps-B.pack` and locally at `build/device-before-20261001.pack`. Only Doodle Jump app/data/icon files and the app pack were written; existing game data and firmware were untouched.

Next: unplug the nano, launch Doodle Jump from the home screen, verify tilt/touch and relaunch, play through deaths/restarts, then reconnect to read `/Apps/Data/DoodleJump/log.txt` for normal-play callback FPS. Actual device sound support and display-idle behaviour remain unresolved.

Installation note: mcopy uses `-n` to suppress overwrite prompts for Unix destinations; `-o` handles DOS destinations only. The final readback reused temporary Unix paths and prompted despite `-o`. Those prompts were answered for the temporary verification files; all checks completed successfully. Future scripted readback must use `-n` or fresh destination paths.
