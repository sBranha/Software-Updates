from pathlib import Path

root = Path('diagnostics/david-616/payload/DavidCompanion')
batch = root / 'ReminderBatchForm.cs'
s = batch.read_text(encoding='utf-8')

old_local = '''\t\t\t\t\tif (IsSharedDone(alertWorkItem))\n\t\t\t\t\t{\n\t\t\t\t\t\tStopEffects();\n\t\t\t\t\t\tShowNext();\n\t\t\t\t\t}'''
new_local = '''\t\t\t\t\tif (IsSharedDone(alertWorkItem))\n\t\t\t\t\t{\n\t\t\t\t\t\tHandleRemoteDone(alertWorkItem.Key);\n\t\t\t\t\t}'''
assert s.count(old_local) == 1, f'Expected one local DONE block, found {s.count(old_local)}'
s = s.replace(old_local, new_local)

old_remote = '''\t\t\t\t\t\tif (done && !finished && index >= 0 && index < items.Count && items[index] != null && string.Equals(items[index].Key, watchedKey, StringComparison.Ordinal))\n\t\t\t\t\t\t{\n\t\t\t\t\t\t\tStopEffects();\n\t\t\t\t\t\t\tShowNext();\n\t\t\t\t\t\t}'''
new_remote = '''\t\t\t\t\t\tif (done)\n\t\t\t\t\t\t{\n\t\t\t\t\t\t\tHandleRemoteDone(watchedKey);\n\t\t\t\t\t\t}'''
assert s.count(old_remote) == 1, f'Expected one cloud DONE block, found {s.count(old_remote)}'
s = s.replace(old_remote, new_remote)

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
