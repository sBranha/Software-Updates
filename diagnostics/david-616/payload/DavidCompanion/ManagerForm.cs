using System;
using System.Collections.Generic;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Threading;
using System.Windows.Forms;

namespace DavidCompanion;

public class ManagerForm : Form
{
	private DataGridView grid = new DataGridView();

	private List<Reminder> current = new List<Reminder>();

	private Label personSummary = new Label();

	private Label reminderSummary = new Label();

	private Label connectionSummary = new Label();

	private Button updateButton = new Button();

	public ManagerForm()
	{
		Text = "David - Caregiver Manager 6.1.6";
		StartPosition = FormStartPosition.CenterScreen;
		Size = new Size(1200, 840);
		MinimumSize = new Size(1080, 740);
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		GradientPanel gradientPanel = new GradientPanel();
		gradientPanel.SetBounds(22, 18, 1140, 118);
		gradientPanel.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
		Controls.Add(gradientPanel);
		Label label = Ui.Label("CAREGIVER", 9, bold: true, Color.FromArgb(244, 184, 81));
		label.SetBounds(28, 14, 150, 22);
		gradientPanel.Controls.Add(label);
		Label label2 = Ui.Label("David Caregiver Manager", 22, bold: true, Color.White);
		label2.SetBounds(26, 37, 500, 42);
		gradientPanel.Controls.Add(label2);
		Label label3 = Ui.Label("Reminders, routines, voice, history, Connected Care, computer status, and safety controls in one place.", 10, bold: false, Color.FromArgb(207, 220, 235));
		label3.SetBounds(29, 82, 700, 25);
		gradientPanel.Controls.Add(label3);
		updateButton = Ui.Button("CHECK UPDATES", Color.FromArgb(191, 126, 42), 8);
		updateButton.SetBounds(576, 42, 185, 44);
		updateButton.Anchor = AnchorStyles.Top | AnchorStyles.Right;
		gradientPanel.Controls.Add(updateButton);
		Button button = updateButton;
		EventHandler value = (object param0, EventArgs param1) =>
		{
			using (UpdateCenterForm updateCenterForm = new UpdateCenterForm())
			{
				updateCenterForm.ShowDialog(this);
			}
			RefreshUpdateStatus();
		};
		button.Click += value;
		Button button2 = Ui.Button("CONNECTED CARE", Theme.Green, 8);
		button2.SetBounds(775, 42, 180, 44);
		button2.Anchor = AnchorStyles.Top | AnchorStyles.Right;
		gradientPanel.Controls.Add(button2);
		button2.Click += (object param0, EventArgs param1) =>
		{
			using (ConnectedCareForm connectedCareForm = new ConnectedCareForm())
			{
				connectedCareForm.ShowDialog(this);
			}
			RefreshGrid();
		};
		Button button3 = Ui.Button("OPEN DAVID", Theme.Blue, 9);
		button3.SetBounds(969, 42, 145, 44);
		button3.Anchor = AnchorStyles.Top | AnchorStyles.Right;
		gradientPanel.Controls.Add(button3);
		button3.Click += (object param0, EventArgs param1) =>
		{
			Program.StartHomeProcess();
		};
		RoundedPanel roundedPanel = Ui.Card(Theme.Surface);
		roundedPanel.SetBounds(22, 151, 350, 82);
		Controls.Add(roundedPanel);
		Label label4 = Ui.Label("PERSON", 8, bold: true, Theme.Gold);
		label4.SetBounds(18, 13, 100, 20);
		roundedPanel.Controls.Add(label4);
		personSummary = Ui.Label("David", 13, bold: true, Theme.Text);
		personSummary.SetBounds(18, 37, 310, 30);
		roundedPanel.Controls.Add(personSummary);
		RoundedPanel roundedPanel2 = Ui.Card(Theme.Surface);
		roundedPanel2.SetBounds(389, 151, 350, 82);
		Controls.Add(roundedPanel2);
		Label label5 = Ui.Label("REMINDERS", 8, bold: true, Theme.Green);
		label5.SetBounds(18, 13, 100, 20);
		roundedPanel2.Controls.Add(label5);
		Button button4 = Ui.Button("DELETE ALL", Color.FromArgb(150, 58, 58), 7);
		button4.SetBounds(218, 10, 112, 28);
		roundedPanel2.Controls.Add(button4);
		button4.Click += (object param0, EventArgs param1) =>
		{
			DeleteAllReminders();
		};
		reminderSummary = Ui.Label("0 active", 13, bold: true, Theme.Text);
		reminderSummary.SetBounds(18, 37, 310, 30);
		roundedPanel2.Controls.Add(reminderSummary);
		RoundedPanel roundedPanel3 = Ui.Card(Theme.Surface);
		roundedPanel3.SetBounds(756, 151, 406, 82);
		roundedPanel3.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
		Controls.Add(roundedPanel3);
		Label label6 = Ui.Label("CONNECTED CARE", 8, bold: true, Theme.Blue);
		label6.SetBounds(18, 13, 140, 20);
		roundedPanel3.Controls.Add(label6);
		connectionSummary = Ui.Label("Local mode", 13, bold: true, Theme.Text);
		connectionSummary.SetBounds(18, 37, 360, 30);
		roundedPanel3.Controls.Add(connectionSummary);
		grid.SetBounds(22, 250, 1140, 390);
		grid.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
		grid.ReadOnly = true;
		grid.AllowUserToAddRows = false;
		grid.MultiSelect = false;
		grid.SelectionMode = DataGridViewSelectionMode.FullRowSelect;
		grid.AutoSizeColumnsMode = DataGridViewAutoSizeColumnsMode.Fill;
		grid.BackgroundColor = Theme.Surface;
		grid.BorderStyle = BorderStyle.None;
		grid.RowHeadersVisible = false;
		grid.EnableHeadersVisualStyles = false;
		grid.ColumnHeadersDefaultCellStyle.BackColor = Theme.Surface3;
		grid.ColumnHeadersDefaultCellStyle.ForeColor = Theme.Text;
		grid.ColumnHeadersDefaultCellStyle.Font = new Font("Segoe UI", 9f, FontStyle.Bold);
		grid.DefaultCellStyle.BackColor = Theme.Surface;
		grid.DefaultCellStyle.ForeColor = Theme.Text;
		grid.DefaultCellStyle.SelectionBackColor = Color.FromArgb(50, 91, 126);
		grid.DefaultCellStyle.SelectionForeColor = Color.White;
		grid.DefaultCellStyle.Font = new Font("Segoe UI", 9f);
		grid.RowTemplate.Height = 36;
		Controls.Add(grid);
		string[] array = new string[8] { "ADD", "EDIT", "DELETE", "TEST", "INSIGHTS", "HISTORY", "SETTINGS", "COMPUTER & SAFETY" };
		int num = 22;
		for (int num2 = 0; num2 < array.Length; num2++)
		{
			int num3 = num2 switch
			{
				7 => 180, 
				6 => 135, 
				_ => 120, 
			};
			Button button5 = Ui.Button(array[num2], (num2 == 0) ? Theme.Green : Color.FromArgb(73, 91, 117), 8);
			button5.SetBounds(num, 660, num3, 44);
			button5.Anchor = AnchorStyles.Bottom | AnchorStyles.Left;
			Controls.Add(button5);
			num += num3 + 9;
			if (num2 == 0)
			{
				button5.Click += (object param0, EventArgs param1) =>
				{
					AddReminder();
				};
			}
			if (num2 == 1)
			{
				button5.Click += (object param0, EventArgs param1) =>
				{
					EditReminder();
				};
			}
			if (num2 == 2)
			{
				button5.Click += (object param0, EventArgs param1) =>
				{
					DeleteReminder();
				};
			}
			if (num2 == 3)
			{
				button5.Click += (object param0, EventArgs param1) =>
				{
					TestReminder();
				};
			}
			if (num2 == 4)
			{
				button5.Click += (object param0, EventArgs param1) =>
				{
					using CaregiverInsightsForm caregiverInsightsForm = new CaregiverInsightsForm();
					caregiverInsightsForm.ShowDialog(this);
				};
			}
			if (num2 == 5)
			{
				button5.Click += (object param0, EventArgs param1) =>
				{
					using HistoryForm historyForm = new HistoryForm();
					historyForm.ShowDialog(this);
				};
			}
			if (num2 == 6)
			{
				button5.Click += (object param0, EventArgs param1) =>
				{
					using CaregiverSettingsForm caregiverSettingsForm = new CaregiverSettingsForm();
					caregiverSettingsForm.ShowDialog(this);
				};
			}
			if (num2 != 7)
			{
				continue;
			}
			button5.Click += (object param0, EventArgs param1) =>
			{
				using (CaregiverToolsForm caregiverToolsForm = new CaregiverToolsForm())
				{
					caregiverToolsForm.ShowDialog(this);
				}
				RefreshGrid();
			};
		}
		Label label7 = Ui.Label("David runs locally on this computer. Your reminders and settings stay on this PC and sync when Connected Care is available.", 9, bold: false, Theme.Muted);
		label7.SetBounds(24, 730, 1110, 30);
		label7.Anchor = AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
		Controls.Add(label7);
		grid.CellDoubleClick += (object param0, DataGridViewCellEventArgs param1) =>
		{
			EditReminder();
		};
		RefreshGrid();
		Shown += (object param0, EventArgs param1) =>
		{
			RefreshUpdateStatus();
		};
	}

	private void RefreshUpdateStatus()
	{
		if (updateButton == null || updateButton.IsDisposed)
		{
			return;
		}
		updateButton.Text = "CHECKING…";
		updateButton.Enabled = false;
		ThreadPool.QueueUserWorkItem((object param0) =>
		{
			bool ok = UpdateService.Check(out var m, out var _);
			try
			{
				BeginInvoke((MethodInvoker)(() =>
				{
					if (updateButton != null && !updateButton.IsDisposed)
					{
						updateButton.Enabled = true;
						updateButton.Text = ((ok && m != null && UpdateService.IsNewer(m.version, "6.1.6")) ? ("UPDATE " + m.version) : "CHECK UPDATES");
					}
				}));
			}
			catch
			{
			}
		});
	}

	private Reminder SelectedReminder()
	{
		if (grid.SelectedRows.Count == 0)
		{
			return null;
		}
		string id = Convert.ToString(grid.SelectedRows[0].Cells["ID"].Value);
		return current.FirstOrDefault((Reminder x) => x.Id == id);
	}

	private void RefreshGrid()
	{
		current = AppData.CompactReminders(AppData.LoadReminders().Items ?? new List<Reminder>());
		if (current.Count != (AppData.LoadReminders().Items ?? new List<Reminder>()).Count)
		{
			AppData.SaveReminders(new ReminderStore
			{
				Items = current
			});
		}
		DataTable dataTable = new DataTable();
		dataTable.Columns.Add("ID");
		dataTable.Columns.Add("On");
		dataTable.Columns.Add("Reminder");
		dataTable.Columns.Add("Category");
		dataTable.Columns.Add("Importance");
		dataTable.Columns.Add("Routine");
		dataTable.Columns.Add("Schedule");
		dataTable.Columns.Add("Time");
		foreach (Reminder item in current)
		{
			if (item != null && !item.Deleted)
			{
				string text;
				if (item.Schedule == "Weekly")
				{
					text = "Weekly - " + item.WeeklyDay;
				}
				else
				{
					text = ((item.Schedule == "Once") ? ("Once - " + item.OnceDate) : item.Schedule);
				}
				string text2 = (TimeSpan.TryParse(item.Time, out var result) ? DateTime.Today.Add(result).ToString("h:mm tt") : item.Time);
				if (Scheduler.IsIntervalSchedule(item.Schedule) && TimeSpan.TryParse(item.EndTime, out var result2))
				{
					text2 = text2 + " – " + DateTime.Today.Add(result2).ToString("h:mm tt");
				}
				string text3 = (string.IsNullOrWhiteSpace(item.RoutineName) ? "" : (item.RoutineName + ((item.RoutineOrder > 0) ? (" #" + item.RoutineOrder) : "")));
				dataTable.Rows.Add(item.Id, item.Enabled ? "Yes" : "No", item.Title, item.Category, string.IsNullOrWhiteSpace(item.Importance) ? "Normal" : item.Importance, text3, text, text2);
			}
		}
		grid.DataSource = dataTable;
		if (grid.Columns["ID"] != null)
		{
			grid.Columns["ID"].Visible = false;
		}
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		string text4;
		if (string.IsNullOrWhiteSpace(securitySettings.PersonPreferredName))
		{
			text4 = (string.IsNullOrWhiteSpace(securitySettings.PersonFullName) ? "David" : securitySettings.PersonFullName);
		}
		else
		{
			text4 = securitySettings.PersonPreferredName;
		}
		string text5 = (securitySettings.PersonLocation + (string.IsNullOrWhiteSpace(securitySettings.PersonRoom) ? "" : ((string.IsNullOrWhiteSpace(securitySettings.PersonLocation) ? "" : " • ") + securitySettings.PersonRoom))).Trim();
		personSummary.Text = text4 + (string.IsNullOrWhiteSpace(text5) ? "" : ("  —  " + text5));
		int num = current.Count((Reminder r) => r != null && !r.Deleted && r.Enabled);
		reminderSummary.Text = num + ((num == 1) ? " active reminder" : " active reminders");
		connectionSummary.Text = (CloudClient.IsConnected() ? "Connected to David Connected Care" : "Local mode — not connected");
		connectionSummary.ForeColor = (CloudClient.IsConnected() ? Theme.Green : Theme.Muted);
	}

	private void AddReminder()
	{
		using EditReminderForm editReminderForm = new EditReminderForm(null);
		if (editReminderForm.ShowDialog(this) == DialogResult.OK && editReminderForm.Value != null)
		{
			editReminderForm.Value.ModifiedUtc = DateTime.UtcNow.ToString("o");
			editReminderForm.Value.Deleted = false;
			List<Reminder> items = AppData.CompactReminders((AppData.LoadReminders().Items ?? new List<Reminder>()).Concat(new Reminder[1] { editReminderForm.Value }));
			AppData.SaveReminders(new ReminderStore
			{
				Items = items
			});
			CloudSyncService.SaveReminderAsync(editReminderForm.Value);
			RefreshGrid();
		}
	}

	private void EditReminder()
	{
		Reminder r = SelectedReminder();
		if (r == null)
		{
			return;
		}
		using EditReminderForm editReminderForm = new EditReminderForm(r);
		if (editReminderForm.ShowDialog(this) == DialogResult.OK && editReminderForm.Value != null)
		{
			editReminderForm.Value.ModifiedUtc = DateTime.UtcNow.ToString("o");
			editReminderForm.Value.Deleted = false;
			List<Reminder> list = AppData.LoadReminders().Items ?? new List<Reminder>();
			int num = list.FindIndex((Reminder x) => x.Id == r.Id);
			if (num >= 0)
			{
				list[num] = editReminderForm.Value;
			}
			AppData.SaveReminders(new ReminderStore
			{
				Items = AppData.CompactReminders(list)
			});
			CloudSyncService.SaveReminderAsync(editReminderForm.Value);
			RefreshGrid();
		}
	}

	private void DeleteReminder()
	{
		Reminder r = SelectedReminder();
		if (r != null && MessageBox.Show("Delete '" + r.Title + "'?", "David", MessageBoxButtons.YesNo, MessageBoxIcon.Question) == DialogResult.Yes)
		{
			List<Reminder> list = AppData.LoadReminders().Items ?? new List<Reminder>();
			list.RemoveAll((Reminder x) => x != null && x.Id == r.Id);
			AppData.SaveReminders(new ReminderStore
			{
				Items = list
			});
			CloudSyncService.DeleteReminderAsync(r.Id);
			RefreshGrid();
		}
	}

	private void DeleteAllReminders()
	{
		int num = (AppData.LoadReminders().Items ?? new List<Reminder>()).Count((Reminder r) => r != null && !r.Deleted);
		if (num == 0 || MessageBox.Show("Delete ALL " + num + " reminders?\r\n\r\nThis clears the reminder list on this Windows computer and the caregiver website.", "David", MessageBoxButtons.YesNo, MessageBoxIcon.Exclamation) != DialogResult.Yes)
		{
			return;
		}
		AppData.SaveReminders(new ReminderStore());
		AppData.SaveState(new StateStore());
		RefreshGrid();
		if (!CloudClient.IsConnected())
		{
			return;
		}
		ThreadPool.QueueUserWorkItem((object param0) =>
		{
			CloudResponse result = null;
			try
			{
				result = CloudClient.ClearAllReminders();
			}
			catch
			{
			}
			try
			{
				BeginInvoke((MethodInvoker)(() =>
				{
					if (result == null || !result.ok)
					{
						MessageBox.Show((result == null) ? "The website could not be reached. The local reminder list is empty, but reconnect before adding new reminders." : result.error, "David", MessageBoxButtons.OK, MessageBoxIcon.Exclamation);
					}
					RefreshGrid();
				}));
			}
			catch
			{
			}
		});
	}

	private void TestReminder()
	{
		Reminder reminder = SelectedReminder();
		if (reminder != null)
		{
			ShowAlert(reminder, DateTime.Now, "test-" + Guid.NewGuid().ToString());
		}
	}

	public static void ShowAlert(Reminder r, DateTime scheduled, string key)
	{
		using ReminderAlertForm reminderAlertForm = new ReminderAlertForm(r, scheduled);
		reminderAlertForm.ShowDialog();
		if (reminderAlertForm.ResultValue == AlertResult.Done)
		{
			AppData.SetState(key, "done", null);
			AppData.AddHistory(r, "Done", key, scheduled);
		}
		else if (reminderAlertForm.ResultValue == AlertResult.Snooze)
		{
			int num = ((r.SnoozeMinutes > 0) ? r.SnoozeMinutes : 10);
			AppData.SetState(key, "snoozed", DateTime.Now.AddMinutes(num));
			AppData.AddHistory(r, "Snoozed", key, scheduled);
		}
		else if (r.RepeatUntilDone || string.Equals(r.Importance, "Important", StringComparison.OrdinalIgnoreCase) || string.Equals(r.Importance, "Critical", StringComparison.OrdinalIgnoreCase) || string.Equals(r.Category, "Medication", StringComparison.OrdinalIgnoreCase))
		{
			int num2 = (string.Equals(r.Importance, "Critical", StringComparison.OrdinalIgnoreCase) ? 3 : 5);
			AppData.SetState(key, "snoozed", DateTime.Now.AddMinutes(num2));
			AppData.AddHistory(r, "Closed - repeats in " + num2 + " minutes", key, scheduled);
		}
		else
		{
			AppData.SetState(key, "dismissed", null);
			AppData.AddHistory(r, "Dismissed", key, scheduled);
		}
	}
}
