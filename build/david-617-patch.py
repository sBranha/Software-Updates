from pathlib import Path
import re

root = Path('diagnostics/david-616/payload/DavidCompanion')
batch = root / 'ReminderBatchForm.cs'
s = batch.read_text(encoding='utf-8')

old_local = '''\t\t\t\t\tif (IsSharedDone(alertWorkItem))\n\t\t\t\t\t{\n\t\t\t\t\t\tStopEffects();\n\t\t\t\t\t\tShowNext();\n\t\t\t\t\t}'''
new_local = '''\t\t\t\t\tif (IsSharedDone(alertWorkItem))\n\t\t\t\t\t{\n\t\t\t\t\t\tHandleRemoteDone(alertWorkItem.Key);\n\t\t\t\t\t}'''
assert s.count(old_local) == 1, f'Expected one local DONE block, found {s.count(old_local)}'
s = s.replace(old_local, new_local)

remote_pattern = re.compile(
    r'(?P<indent>^[ \t]*)if \(done && !finished && index >= 0 && index < items\.Count && items\[index\] != null && string\.Equals\(items\[index\]\.Key, watchedKey, StringComparison\.Ordinal\)\)\s*\r?\n'
    r'(?P=indent)\{\s*\r?\n'
    r'(?P=indent)\tStopEffects\(\);\s*\r?\n'
    r'(?P=indent)\tShowNext\(\);\s*\r?\n'
    r'(?P=indent)\}',
    re.MULTILINE
)

def replace_remote(match):
    indent = match.group('indent')
    return (
        f'{indent}if (done)\n'
        f'{indent}{{\n'
        f'{indent}\tHandleRemoteDone(watchedKey);\n'
        f'{indent}}}'
    )

s, remote_count = remote_pattern.subn(replace_remote, s)
assert remote_count == 1, f'Expected one cloud DONE block, found {remote_count}'

marker = '\n\tprivate void ShowNext()\n'
assert s.count(marker) == 1, 'ShowNext insertion point changed'
helper = r'''
	private void HandleRemoteDone(string occurrenceKey)
	{
		if (string.IsNullOrWhiteSpace(occurrenceKey))
		{
			return;
		}

		AppData.SetState(occurrenceKey, "done", null);

		if (finished)
		{
			allowClose = true;
			Close();
			return;
		}

		if (index < 0 || index >= items.Count || items[index] == null ||
			!string.Equals(items[index].Key, occurrenceKey, StringComparison.Ordinal))
		{
			return;
		}

		StopEffects();

		for (int i = index + 1; i < items.Count; i++)
		{
			AlertWorkItem candidate = items[i];
			if (candidate != null && candidate.Reminder != null && !IsSharedDone(candidate))
			{
				ShowNext();
				return;
			}
		}

		finished = true;
		allowClose = true;
		Close();
	}
'''
s = s.replace(marker, '\n' + helper + marker)
batch.write_text(s, encoding='utf-8')

for p in Path('diagnostics/david-616/payload').rglob('*.cs'):
    text = p.read_text(encoding='utf-8')
    if '6.1.6' in text:
        p.write_text(text.replace('6.1.6', '6.1.7'), encoding='utf-8')

boot = Path('diagnostics/david-616/bootstrapper')
for p in list(boot.rglob('*.cs')) + list(boot.rglob('*.csproj')):
    text = p.read_text(encoding='utf-8')
    if '6.1.6' in text:
        p.write_text(text.replace('6.1.6', '6.1.7'), encoding='utf-8')

print('David 6.1.7 patch applied successfully.')
