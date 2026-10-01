# Doodle Jump 1.0 → iPod nano 7G

Work in progress toward a port of the original iOS Doodle Jump 1.0. **The nano test build compiles, but has not been installed or validated on hardware. This is not yet a finished full decompilation port.**

The target is the original native game's behaviour, assets and flow. Gameplay is translated into C from local decompiler output, using the original artwork and all 19 obstacle arrangements. Arithmetic, random generation, collision order and object updates are compared against original ARM instructions. UIKit, graphics upload, input and storage are adapted to NanoApps.

## What is verified

- 205 named Objective-C methods exported for local analysis.
- 17,000 object updates compared: platforms, three monster types, UFOs, springs and projectiles.
- Initial platform layouts match the original for five seeds (105 platforms), using the Darwin random-number algorithm.
- 320 generated platforms at four difficulty heights, including independent springs.
- All 19 original scene arrangements, with 156 scene objects plus their springs.
- 144 individual collision cases and 150 overlapping-enemy/projectile/UFO-rescue scenarios.
- 1,638 main-loop frames, covering bouncing, scrolling, scoring, falling death, both captures and changing tilt. Facing and jumping texture timing are also checked on the zero-tilt/capture runs.
- 1,000 accelerometer filter samples give identical double-precision results.
- The Mac and cross-compiled ARM simulation produce the same `0920542b` state digest. QEMU does not measure nano performance.
- Host playthroughs cover the original menu, button artwork, fades, shooting, game-over layout and saved high scores.

The reference harness supplies Objective-C dispatch, standard arithmetic and libm imports. These checks validate the named routines and scenarios; they do not establish whole-game or device equivalence.

## Remaining work

Launch/relaunch, tilt direction, touch coordinates, display sleep and actual frame rate need hardware tests. Nano audio is currently silent: the SDK's single-shot audio API does not reproduce overlapping effects and the looping UFO sound. The Mac interactive build has an OpenAL adapter for the 13 original effects, but listening validation remains outstanding.

The online leaderboard is explicitly adapted to an offline local-best page; no original network service is contacted. iOS vibration and UIKit/network services are platform differences. Exported decompiler output and passing comparisons do not establish whole-program equivalence or a binary-matching rebuild.

The nano build uses ten preloaded texture pages (1.25 MiB), a 512 KiB allocation arena and fixed-capacity object pools. A run-end log measures callback frame rate and submission time without an on-screen counter or per-frame disk writes. Display idle behaviour still needs checking because the SDK wake lock currently targets LVGL apps.

## Local host build

Requires macOS, a C compiler, Pillow and a local copy of the inspected 1.0 app. The original executable SHA-256 is `68b29c08bcbe25e760dfd92aee05d92d3f3cae394789e7a029c53b210ff0e6d4`.

```sh
python3 -m venv .venv
.venv/bin/pip install Pillow unicorn
.venv/bin/python tools/convert_assets.py original/v1.0/Payload/DoodleJump.app build/data
make -C port
./port/build/djhost -d build/data -S build/save -w
```

Use A/D or left/right arrows for tilt, release the mouse button to shoot, and Q to quit. The original 320×480 canvas is displayed at 240×360 with margins on a 240×432 panel. Tilt filtering runs at 100 Hz independently of the 60 Hz simulation, using the latest sample available from the platform adapter. Hardware performance is unmeasured.

## Original-code comparisons

The original executable and converted scene data are needed locally. On macOS, Unicorn needs permission to use its JIT. Generate the method/ivar metadata before running comparisons:

```sh
python3 tools/inspect_original.py original/v1.0/Payload/DoodleJump.app/DoodleJump
cc -O2 -ffp-contract=off -dynamiclib -Iport/src port/src/objects.c -o build/objects.dylib
.venv/bin/python tests/compare_objects.py
cc -O2 -ffp-contract=off -dynamiclib -Iport/src tests/core_probe.c tests/platform_stubs.c \
  port/src/game.c port/src/objects.c port/src/random.c -o build/core.dylib
.venv/bin/python tests/compare_core.py
.venv/bin/python tests/compare_loop.py
.venv/bin/python tests/compare_scenes.py
.venv/bin/python tests/compare_projectiles.py
.venv/bin/python tests/compare_tilt.py
```

`ghidra/scripts/ExportGame.java` exports the named methods with corrected ARM floating-point import signatures. See [port evidence](analysis/port-notes.md) for addresses and analysis details.

## Nano build and staging

Use the existing NanoApps SDK and `arm-none-eabi-gcc`. Copy `port/nano/app/` into the SDK's `apps/doodlejump/` directory. Keep the SDK's Thumb-FP validation gate enabled.

```sh
make -C port/nano NANOAPPS=/path/to/NanoApps
make -C /path/to/NanoApps/apps/doodlejump DJ_DIR=/path/to/nano-doodlejump/port
.venv/bin/python tools/build_icon.py --sdk /path/to/NanoApps \
  --source original/v1.0/Payload/DoodleJump.app --out build/icon.png
.venv/bin/python tools/stage_nano.py --sdk /path/to/NanoApps \
  --app build/doodlejump.hbapp --data build/data --icon build/icon.png \
  --base-pack /path/to/current/AllApps-B.pack --out build/nano-stage
```

Copy the built `doodlejump.hbapp` into `build/` before staging. The staging tool creates files locally and preserves all other app bundles byte-for-byte. Use a fresh copy of the device's current pack and verify its recorded SHA-256 before installation. Staging does not install anything or alter firmware.

The icon uses the original Doodler sprite inside the existing NanoApps circular frame, matching the Mario/Angry Birds apps. No generated artwork is used.

## Data and attribution

Game packages, artwork, scene data, binaries and raw decompiler output stay local and are ignored by Git. The converter reads them from the original app; it does not embed game data in the repository. Doodle Jump belongs to its respective owners.

The renderer was adapted from the existing nano Angry Birds port. The random generator is adapted from Apple's published Libc-594.1.4 source; its copyright and licence are retained in `port/src/random.c`. This product includes software developed by the University of California, Berkeley and its contributors.
