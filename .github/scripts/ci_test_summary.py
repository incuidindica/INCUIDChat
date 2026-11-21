#!/usr/bin/env python3
"""Summarize Gradle JUnit XML without introducing external Python dependencies."""
import os
from pathlib import Path
import xml.etree.ElementTree as ET

reports = sorted(Path('build/test-results/test').glob('TEST-*.xml'))
summary = ['## INCUID Chat build verification', '']
if not reports:
    summary += [
        'JUnit XML results are not available.',
        'This may mean compilation or test initialization failed. Open the',
        '`INCUIDChat-v1.4.0-build-reports` artifact and inspect `ci-logs/gradle-build.log`.',
    ]
else:
    total = failures = errors = skipped = 0
    bad_suites = []
    for path in reports:
        try:
            tree = ET.parse(path)
            root = tree.getroot()
            suites = [root] if root.tag == 'testsuite' else root.findall('.//testsuite')
            for suite in suites:
                total += int(suite.get('tests', '0'))
                failures += int(suite.get('failures', '0'))
                errors += int(suite.get('errors', '0'))
                skipped += int(suite.get('skipped', '0'))
                if int(suite.get('failures', '0')) + int(suite.get('errors', '0')):
                    bad_suites.append(suite.get('name', path.name))
        except (ET.ParseError, ValueError) as exc:
            bad_suites.append(f'{path.name}: unreadable XML ({exc})')
    summary += [
        '| Metric | Count |', '|---|---:|',
        f'| Tests | {total} |',
        f'| Failures | {failures} |',
        f'| Errors | {errors} |',
        f'| Skipped | {skipped} |',
        '',
    ]
    if bad_suites:
        summary += ['**Suites requiring attention:**'] + [f'- `{name}`' for name in bad_suites]
    else:
        summary += ['No failures reported in the available JUnit XML files.']
    summary += ['', 'Download the build-reports artifact for HTML reports and the complete build log.']
text = '\n'.join(summary) + '\n'
print(text)
summary_file = os.environ.get('GITHUB_STEP_SUMMARY')
if summary_file:
    with open(summary_file, 'a', encoding='utf-8') as stream:
        stream.write(text)
