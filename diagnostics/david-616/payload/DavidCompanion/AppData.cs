using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Threading;
using System.Xml.Serialization;

namespace DavidCompanion;

public static class AppData
{
	public static readonly string Root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "David");

	public static readonly string LegacyRoot = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "ForgeReminder");

	public static readonly string RemindersPath = Path.Combine(Root, "reminders.xml");

	public static readonly string StatePath = Path.Combine(Root, "state.xml");

	public static readonly string HistoryPath = Path.Combine(Root, "history.xml");

	public static readonly string SettingsPath = Path.Combine(Root, "security.xml");

	public static readonly string ImagesRoot = Path.Combine(Root, "images");

	public static readonly object LocalLock = new object();

	public static string BackupsRoot => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Personal), "David Backups");

	public static void Ensure()
	{
		Directory.CreateDirectory(Root);
		Directory.CreateDirectory(ImagesRoot);
		MigrateLegacyFile("reminders.xml");
		MigrateLegacyFile("state.xml");
		MigrateLegacyFile("history.xml");
		MigrateLegacyFile("security.xml");
		if (!File.Exists(RemindersPath))
		{
			SaveReminders(new ReminderStore());
		}
		if (!File.Exists(StatePath))
		{
			SaveState(new StateStore());
		}
		if (!File.Exists(HistoryPath))
		{
			SaveHistory(new HistoryStore());
		}
		if (!File.Exists(SettingsPath))
		{
			SaveSettings(new SecuritySettings());
		}
		NormalizeReminderMetadata();
		CompactLegacyReminderData();
	}

	private static void NormalizeReminderMetadata()
	{
		try
		{
			ReminderStore reminderStore = LoadReminders();
			bool flag = false;
			string modifiedUtc = DateTime.UtcNow.ToString("o");
			foreach (Reminder item in reminderStore.Items ?? new List<Reminder>())
			{
				if (string.IsNullOrWhiteSpace(item.Id))
				{
					item.Id = Guid.NewGuid().ToString();
					flag = true;
				}
				if (string.IsNullOrWhiteSpace(item.ModifiedUtc))
				{
					item.ModifiedUtc = modifiedUtc;
					flag = true;
				}
			}
			if (flag)
			{
				SaveReminders(reminderStore);
			}
		}
		catch
		{
		}
	}

	private static string ReminderText(string value)
	{
		return (value ?? "").Trim().ToLowerInvariant();
	}

	private static string ReminderSignature(Reminder r)
	{
		if (r == null)
		{
			return "";
		}
		string text = ReminderText(r.Schedule);
		return string.Join("|", ReminderText(r.Title), ReminderText(r.Message), ReminderText(r.Category), text, ReminderText(r.Time), ReminderText(r.EndTime), r.IntervalHours.ToString(CultureInfo.InvariantCulture), (text == "weekly") ? ReminderText(r.WeeklyDay) : "", (text == "once") ? ReminderText(r.OnceDate) : "", ReminderText(r.Importance), ReminderText(r.RoutineName), ReminderText(r.MedicationDose), ReminderText(r.MedicationInstructions), ReminderText(r.AppointmentWhere), ReminderText(r.SpokenText));
	}

	public static List<Reminder> CompactReminders(IEnumerable<Reminder> source)
	{
		List<Reminder> list = (from r in source ?? Enumerable.Empty<Reminder>()
			where r != null && !r.Deleted
			orderby DateTime.TryParse(r.ModifiedUtc, null, DateTimeStyles.RoundtripKind, out var result) ? result.ToUniversalTime() : DateTime.MinValue descending
			select r).ToList();
		HashSet<string> hashSet = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
		List<Reminder> list2 = new List<Reminder>();
		foreach (Reminder item2 in list)
		{
			string item = ReminderSignature(item2);
			if (hashSet.Add(item))
			{
				list2.Add(item2);
			}
		}
		return list2.OrderBy((Reminder r) => r.Time ?? "").ThenBy((Reminder r) => r.Title ?? "", StringComparer.OrdinalIgnoreCase).ToList();
	}

	private static void CompactLegacyReminderData()
	{
		try
		{
			ReminderStore reminderStore = LoadReminders();
			List<Reminder> list = CompactReminders(reminderStore.Items ?? new List<Reminder>());
			if ((reminderStore.Items ?? new List<Reminder>()).Count != list.Count)
			{
				SaveReminders(new ReminderStore
				{
					Items = list
				});
			}
			HistoryStore historyStore = LoadHistory();
			List<HistoryEntry> list2 = historyStore.Items ?? new List<HistoryEntry>();
			if (list2.Count > 250)
			{
				historyStore.Items = list2.Skip(list2.Count - 250).ToList();
				SaveHistory(historyStore);
			}
		}
		catch
		{
		}
	}

	private static void MigrateLegacyFile(string name)
	{
		try
		{
			string text = Path.Combine(LegacyRoot, name);
			string text2 = Path.Combine(Root, name);
			if (!File.Exists(text2) && File.Exists(text))
			{
				File.Copy(text, text2, overwrite: false);
			}
		}
		catch
		{
		}
	}

	private static bool TryRead<T>(string path, out T value) where T : new()
	{
		value = new T();
		try
		{
			if (!File.Exists(path))
			{
				return false;
			}
			using FileStream stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
			object obj = new XmlSerializer(typeof(T)).Deserialize(stream);
			if (obj == null)
			{
				return false;
			}
			value = (T)obj;
			return true;
		}
		catch
		{
			return false;
		}
	}

	private static T Load<T>(string path) where T : new()
	{
		lock (LocalLock)
		{
			for (int i = 0; i < 4; i++)
			{
				if (TryRead<T>(path, out var value))
				{
					return value;
				}
				Thread.Sleep(60);
			}
			string text = path + ".good";
			if (TryRead<T>(text, out var value2))
			{
				try
				{
					File.Copy(text, path, overwrite: true);
				}
				catch
				{
				}
				return value2;
			}
			return new T();
		}
	}

	private static void Save<T>(T value, string path) where T : new()
	{
		lock (LocalLock)
		{
			Directory.CreateDirectory(Root);
			string text = path + ".tmp";
			using (FileStream stream = new FileStream(text, FileMode.Create, FileAccess.Write, FileShare.None))
			{
				new XmlSerializer(typeof(T)).Serialize(stream, value);
			}
			try
			{
				if (File.Exists(path) && TryRead<T>(path, out var _))
				{
					File.Copy(path, path + ".good", overwrite: true);
				}
			}
			catch
			{
			}
			File.Copy(text, path, overwrite: true);
			try
			{
				File.Delete(text);
			}
			catch
			{
			}
		}
	}

	public static ReminderStore LoadReminders()
	{
		return Load<ReminderStore>(RemindersPath);
	}

	public static StateStore LoadState()
	{
		return Load<StateStore>(StatePath);
	}

	public static HistoryStore LoadHistory()
	{
		return Load<HistoryStore>(HistoryPath);
	}

	public static SecuritySettings LoadSettings()
	{
		return Load<SecuritySettings>(SettingsPath);
	}

	public static void SaveReminders(ReminderStore s)
	{
		Save(s, RemindersPath);
	}

	public static void SaveState(StateStore s)
	{
		Save(s, StatePath);
	}

	public static void SaveHistory(HistoryStore s)
	{
		Save(s, HistoryPath);
	}

	public static void SaveSettings(SecuritySettings s)
	{
		Save(s, SettingsPath);
	}

	public static StateEntry GetState(string key)
	{
		return LoadState().Items.FirstOrDefault((StateEntry x) => x.Key == key);
	}

	public static void SetState(string key, string status, DateTime? until)
	{
		StateStore stateStore = LoadState();
		stateStore.Items.RemoveAll((StateEntry x) => x.Key == key);
		stateStore.Items.Add(new StateEntry
		{
			Key = key,
			Status = status,
			Updated = DateTime.Now.ToString("o"),
			Until = (until.HasValue ? until.Value.ToString("o") : "")
		});
		DateTime cutoff = DateTime.Now.AddDays(-21.0);
		stateStore.Items = stateStore.Items.Where((StateEntry x) => !DateTime.TryParse(x.Updated, out var result) || result >= cutoff).ToList();
		SaveState(stateStore);
	}

	public static void AddHistory(Reminder r, string action, string key, DateTime scheduled)
	{
		HistoryStore historyStore = LoadHistory();
		historyStore.Items.Add(new HistoryEntry
		{
			Timestamp = DateTime.Now.ToString("o"),
			ReminderId = r.Id,
			Title = r.Title,
			Action = action,
			OccurrenceKey = key,
			ScheduledFor = scheduled.ToString("o")
		});
		if (historyStore.Items.Count > 250)
		{
			historyStore.Items = historyStore.Items.Skip(historyStore.Items.Count - 250).ToList();
		}
		SaveHistory(historyStore);
		if (!string.IsNullOrWhiteSpace(action) && action.StartsWith("Done", StringComparison.OrdinalIgnoreCase))
		{
			try
			{
				CloudSyncService.SyncAsync();
			}
			catch
			{
			}
		}
	}

	public static void AddSystemHistory(string title, string action, string key, DateTime scheduled)
	{
		HistoryStore historyStore = LoadHistory();
		historyStore.Items.Add(new HistoryEntry
		{
			Timestamp = DateTime.Now.ToString("o"),
			ReminderId = "system",
			Title = (title ?? "David"),
			Action = (action ?? ""),
			OccurrenceKey = (key ?? ""),
			ScheduledFor = scheduled.ToString("o")
		});
		if (historyStore.Items.Count > 250)
		{
			historyStore.Items = historyStore.Items.Skip(historyStore.Items.Count - 250).ToList();
		}
		SaveHistory(historyStore);
	}

	public static string Backup()
	{
		return CreateBackup(automatic: false);
	}

	public static string CreateBackup(bool automatic)
	{
		string backupsRoot = BackupsRoot;
		Directory.CreateDirectory(backupsRoot);
		string text = (automatic ? "Auto Backup " : "David Backup ");
		string text2 = Path.Combine(backupsRoot, text + DateTime.Now.ToString("yyyy-MM-dd HHmmss"));
		Directory.CreateDirectory(text2);
		CopyIfExists(RemindersPath, Path.Combine(text2, "reminders.xml"));
		CopyIfExists(StatePath, Path.Combine(text2, "state.xml"));
		CopyIfExists(HistoryPath, Path.Combine(text2, "history.xml"));
		CopyIfExists(SettingsPath, Path.Combine(text2, "security.xml"));
		try
		{
			if (Directory.Exists(ImagesRoot))
			{
				string text3 = Path.Combine(text2, "images");
				Directory.CreateDirectory(text3);
				string[] files = Directory.GetFiles(ImagesRoot);
				foreach (string text4 in files)
				{
					File.Copy(text4, Path.Combine(text3, Path.GetFileName(text4)), overwrite: true);
				}
			}
		}
		catch
		{
		}
		File.WriteAllText(Path.Combine(text2, "README.txt"), "David 5.8 backup created " + DateTime.Now.ToString("F") + Environment.NewLine + "This backup contains David's local reminders, history, settings, and reminder pictures.");
		if (automatic)
		{
			SecuritySettings securitySettings = LoadSettings() ?? new SecuritySettings();
			PruneAutoBackups((securitySettings.AutoBackupRetention <= 0) ? 7 : securitySettings.AutoBackupRetention);
		}
		return text2;
	}

	public static void AutoBackupIfNeeded()
	{
		try
		{
			if (!string.Equals((LoadSettings() ?? new SecuritySettings()).AutoBackupEnabled, "off", StringComparison.OrdinalIgnoreCase))
			{
				string key = "automatic-local-backup-" + DateTime.Now.ToString("yyyy-MM-dd");
				if (GetState(key) == null && ValidateData(out var _))
				{
					string path = CreateBackup(automatic: true);
					SetState(key, "done", null);
					AddSystemHistory("Automatic Backup", "Created " + Path.GetFileName(path), key, DateTime.Now);
				}
			}
		}
		catch
		{
		}
	}

	public static List<string> GetBackupFolders()
	{
		try
		{
			if (!Directory.Exists(BackupsRoot))
			{
				return new List<string>();
			}
			return (from x in Directory.GetDirectories(BackupsRoot)
				orderby Directory.GetCreationTime(x) descending
				select x).ToList();
		}
		catch
		{
			return new List<string>();
		}
	}

	public static bool ValidateData(out string detail)
	{
		bool flag = TryRead<ReminderStore>(RemindersPath, out var _);
		bool flag2 = TryRead<StateStore>(StatePath, out var _);
		bool flag3 = TryRead<HistoryStore>(HistoryPath, out var _);
		bool flag4 = TryRead<SecuritySettings>(SettingsPath, out var _);
		if (flag & flag2 & flag3 & flag4)
		{
			detail = "Reminders, state, history, and settings are readable.";
			return true;
		}
		List<string> list = new List<string>();
		if (!flag)
		{
			list.Add("reminders");
		}
		if (!flag2)
		{
			list.Add("state");
		}
		if (!flag3)
		{
			list.Add("history");
		}
		if (!flag4)
		{
			list.Add("settings");
		}
		detail = "Problem reading: " + string.Join(", ", list.ToArray()) + ".";
		return false;
	}

	public static bool RestoreBackup(string folder, out string error)
	{
		error = "";
		try
		{
			if (string.IsNullOrWhiteSpace(folder) || !Directory.Exists(folder))
			{
				error = "That backup folder does not exist.";
				return false;
			}
			string text = Path.Combine(folder, "reminders.xml");
			string text2 = Path.Combine(folder, "state.xml");
			string text3 = Path.Combine(folder, "history.xml");
			string text4 = Path.Combine(folder, "security.xml");
			if (!TryRead<ReminderStore>(text, out var _) || !TryRead<StateStore>(text2, out var _) || !TryRead<HistoryStore>(text3, out var _) || !TryRead<SecuritySettings>(text4, out var _))
			{
				error = "The selected folder is not a complete, readable David backup.";
				return false;
			}
			string path = CreateBackup(automatic: false);
			lock (LocalLock)
			{
				File.Copy(text, RemindersPath, overwrite: true);
				File.Copy(text2, StatePath, overwrite: true);
				File.Copy(text3, HistoryPath, overwrite: true);
				File.Copy(text4, SettingsPath, overwrite: true);
				string path2 = Path.Combine(folder, "images");
				if (Directory.Exists(path2))
				{
					Directory.CreateDirectory(ImagesRoot);
					string[] files = Directory.GetFiles(path2);
					foreach (string text5 in files)
					{
						File.Copy(text5, Path.Combine(ImagesRoot, Path.GetFileName(text5)), overwrite: true);
					}
				}
			}
			AddSystemHistory("Restore", "Restored local backup. Safety backup: " + Path.GetFileName(path), "restore-" + DateTime.Now.ToString("yyyyMMddHHmmss"), DateTime.Now);
			return true;
		}
		catch (Exception ex)
		{
			error = ex.Message;
			return false;
		}
	}

	private static void PruneAutoBackups(int keep)
	{
		try
		{
			keep = Math.Max(2, Math.Min(30, keep));
			if (!Directory.Exists(BackupsRoot))
			{
				return;
			}
			foreach (string item in (from x in Directory.GetDirectories(BackupsRoot, "Auto Backup *")
				orderby Directory.GetCreationTime(x) descending
				select x).ToList().Skip(keep))
			{
				try
				{
					Directory.Delete(item, recursive: true);
				}
				catch
				{
				}
			}
		}
		catch
		{
		}
	}

	private static void CopyIfExists(string from, string to)
	{
		if (File.Exists(from))
		{
			File.Copy(from, to, overwrite: true);
		}
	}

	public static string ImportPicture(string source)
	{
		if (string.IsNullOrWhiteSpace(source) || !File.Exists(source))
		{
			return "";
		}
		Directory.CreateDirectory(ImagesRoot);
		string text = Path.GetExtension(source);
		if (string.IsNullOrEmpty(text))
		{
			text = ".jpg";
		}
		string text2 = Path.Combine(ImagesRoot, "reminder-" + Guid.NewGuid().ToString("N") + text.ToLowerInvariant());
		File.Copy(source, text2, overwrite: true);
		return text2;
	}

	public static string ResolvePicture(string path)
	{
		if (string.IsNullOrWhiteSpace(path))
		{
			return "";
		}
		if (File.Exists(path))
		{
			return path;
		}
		string text = Path.Combine(ImagesRoot, Path.GetFileName(path));
		if (!File.Exists(text))
		{
			return "";
		}
		return text;
	}
}
