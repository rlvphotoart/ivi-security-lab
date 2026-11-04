#!/usr/bin/env python3
"""UI-driven smoke check on a disposable emulator; no vehicle is selected implicitly."""
import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
args = parser.parse_args()
if not args.serial.startswith('emulator-'):
    parser.error('This smoke script installs apps and clears their data; use a disposable emulator.')
root = Path(__file__).resolve().parents[1]
out = root / 'outputs' / args.serial
out.mkdir(parents=True, exist_ok=True)


def adb(*parts, binary=False):
    r = subprocess.run(['adb', '-s', args.serial, *parts], capture_output=True, timeout=35, check=True)
    return r.stdout if binary else r.stdout.decode()


def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/ivi-lab-smoke.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/ivi-lab-smoke.xml'))


def tap(label):
    for attempt in range(8):
        for node in tree().iter('node'):
            if node.get('text') == label or node.get('content-desc') == label:
                x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
                if x2 <= x1 or y2 <= y1:
                    continue
                adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
                time.sleep(.4)
                return
        size = list(map(int, re.findall(r'(\d+)x(\d+)', adb('shell', 'wm', 'size'))[-1]))
        adb('shell', 'input', 'swipe', str(size[0]//2), str(size[1]*4//5), str(size[0]//2), str(size[1]//3), '250')
    raise AssertionError('Not visible: ' + label)


def launch(component):
    adb('shell', 'am', 'start', '-n', component)
    time.sleep(.5)


for module, package in [('probe', 'com.rlv.ivilab'), ('fixture', 'com.rlv.ivilabfixture')]:
    adb('install', '-r', str(root / 'outputs' / f'ivi-lab-{module}.apk'))
    adb('shell', 'pm', 'clear', package)
adb('logcat', '-c')
launch('com.rlv.ivilabfixture/.FixtureActivity')
fixture_xml = ET.tostring(tree(), encoding='unicode')
assert 'READY — self-check passed' in fixture_xml, fixture_xml
(out / 'fixture-self-check.xml').write_text(fixture_xml)
launch('com.rlv.ivilab/.MainActivity')
initial = ET.tostring(tree(), encoding='unicode')
assert 'Phone → vehicle IVI' in initial
(out / 'phone-only-initial.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
tap('1. Check permission boundary (before grant)')
tap('Local')
tap('Run containment checks')
time.sleep(1)
tap('Evidence')
tap('Phone')
tap('Emulator / bench')
tap('Save notes')
(out / 'evidence.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
tap('Export JSON report')
time.sleep(.6)
tap('SAVE')
time.sleep(.6)
files = adb('shell', 'ls', '-t', '/sdcard/Download/ivi-lab-*.json').splitlines()
assert files, 'Export not found in Downloads'
adb('pull', files[0], str(out / 'report.json'))
report = json.loads((out / 'report.json').read_text())
events = report['events']
file_results = [e['verdict'] for e in events if e['id'] == 'IVI-02.FILE']
assert file_results and file_results[0] in ('PASS', 'INCONCLUSIVE'), file_results
assert any(e['id'] == 'IVI-02.PROVIDER' and e['verdict'] == 'PASS' for e in events)
assert any(e['id'] == 'IVI-02.COMPLETE' for e in events)
assert not any(e['verdict'] == 'FAIL' for e in events), events
adb('shell', 'am', 'force-stop', 'com.rlv.ivilab')
launch('com.rlv.ivilab/.MainActivity')
tap('Evidence')
xml = ET.tostring(tree(), encoding='unicode')
assert 'Containment checks finished' in xml, 'Report did not survive process restart'
(out / 'evidence-restored.xml').write_text(xml)
tap('IVI')
tap('2. Grant Nearby devices permissions')
tap('Allow')
tap('1. Check permission boundary (before grant)')
tap('Request IVI service discovery (SDP)')
tap('Attempt secure connection • no payload')
tap('Observe selected IVI audio / call profiles')
tap('IVI')
tap('Read cached service UUIDs')
log = adb('logcat', '-d', '-s', 'IVILab:I', 'AndroidRuntime:E', '*:S')
(out / 'runtime.log').write_text(log)
assert 'Permission already granted' in log
assert 'No peer selected' in log
assert 'FATAL EXCEPTION' not in log
(out / 'preview.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
summary = {'serial': args.serial, 'app_version': report['app_version'], 'android': report['device']['android_release'], 'sdk': report['device']['sdk'], 'phone_only_screen': True, 'no_target_guards': True, 'fixture_ready': True, 'private_file_verdict': file_results[0], 'private_provider_denied': True, 'json_export': True, 'process_restart_persistence': True, 'permission_prompt': True, 'physical_bluetooth_tested': False, 'native_sdp_roundtrip_tested': False, 'native_rfcomm_roundtrip_tested': False, 'cve_reproduction_tested': False, 'events': len(events)}
(out / 'summary.json').write_text(json.dumps(summary, indent=2))
print(json.dumps(summary, indent=2))
