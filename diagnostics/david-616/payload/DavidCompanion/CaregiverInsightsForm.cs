using System;
using System.Collections.Generic;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;

namespace DavidCompanion;

public class CaregiverInsightsForm : Form
{
	private ComboBox rangeBox = new ComboBox();

	private Label completion = new Label();

	private Label medication = new Label();

	private Label checkins = new Label();

	private Label pattern = new Label();

	private DataGridView grid = new DataGridView();

	private string clipboardSummary = "";

	public CaregiverInsightsForm()
	{
		Text = "David - Caregiver Insights";
		StartPosition = FormStartPosition.CenterParent;
		Size = new Size(1060, 720);
		MinimumSize = new Size(980, 650);
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		Label label = Ui.Label("Caregiver Insights", 22, bold: true, Theme.Text);
		label.SetBounds(24, 18, 420, 44);
		Controls.Add(label);
		Label label2 = Ui.Label("See reminder, medication, and I'M OKAY patterns over time. This is an activity summary, not a medical assessment.", 10, bold: false, Theme.Muted);
		label2.SetBounds(27, 61, 780, 42);
		Controls.Add(label2);
		Label label3 = Ui.Label("Show", 9, bold: true, Theme.Text);
		label3.SetBounds(830, 28, 52, 26);
		Controls.Add(label3);
		rangeBox.SetBounds(875, 24, 145, 32);
		rangeBox.DropDownStyle = ComboBoxStyle.DropDownList;
		rangeBox.Items.Add("Last 7 days");
		rangeBox.Items.Add("Last 14 days");
		rangeBox.Items.Add("Last 30 days");
		rangeBox.SelectedIndex = 0;
		Controls.Add(rangeBox);
		RoundedPanel roundedPanel = Ui.Card(Theme.Surface);
		roundedPanel.SetBounds(24, 112, 235, 88);
		Controls.Add(roundedPanel);
		Label label4 = Ui.Label("REMINDERS", 8, bold: true, Theme.Blue);
		label4.SetBounds(16, 12, 180, 20);
		roundedPanel.Controls.Add(label4);
		completion = Ui.Label("—", 15, bold: true, Theme.Text);
		completion.SetBounds(16, 38, 205, 38);
		roundedPanel.Controls.Add(completion);
		RoundedPanel roundedPanel2 = Ui.Card(Theme.Surface);
		roundedPanel2.SetBounds(272, 112, 235, 88);
		Controls.Add(roundedPanel2);
		Label label5 = Ui.Label("MEDICATION", 8, bold: true, Theme.Green);
		label5.SetBounds(16, 12, 180, 20);
		roundedPanel2.Controls.Add(label5);
		medication = Ui.Label("—", 15, bold: true, Theme.Text);
		medication.SetBounds(16, 38, 205, 38);
		roundedPanel2.Controls.Add(medication);
		RoundedPanel roundedPanel3 = Ui.Card(Theme.Surface);
		roundedPanel3.SetBounds(520, 112, 235, 88);
		Controls.Add(roundedPanel3);
		Label label6 = Ui.Label("I'M OKAY", 8, bold: true, Theme.Gold);
		label6.SetBounds(16, 12, 180, 20);
		roundedPanel3.Controls.Add(label6);
		checkins = Ui.Label("—", 15, bold: true, Theme.Text);
		checkins.SetBounds(16, 38, 205, 38);
		roundedPanel3.Controls.Add(checkins);
		RoundedPanel roundedPanel4 = Ui.Card(Theme.Surface);
		roundedPanel4.SetBounds(768, 112, 264, 88);
		Controls.Add(roundedPanel4);
		Label label7 = Ui.Label("MOST OFTEN INCOMPLETE", 8, bold: true, Theme.Danger);
		label7.SetBounds(16, 12, 220, 20);
		roundedPanel4.Controls.Add(label7);
		pattern = Ui.Label("—", 10, bold: true, Theme.Text);
		pattern.SetBounds(16, 35, 230, 45);
		roundedPanel4.Controls.Add(pattern);
		grid.SetBounds(24, 216, 1008, 380);
		grid.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
		grid.ReadOnly = true;
		grid.AllowUserToAddRows = false;
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
		grid.RowTemplate.Height = 34;
		Controls.Add(grid);
		Button button = Ui.Button("TODAY DETAIL", Color.FromArgb(73, 91, 117), 9);
		button.SetBounds(24, 615, 180, 42);
		button.Anchor = AnchorStyles.Bottom | AnchorStyles.Left;
		Controls.Add(button);
		EventHandler value = (object param0, EventArgs param1) =>
		{
			using DailyReportForm dailyReportForm = new DailyReportForm();
			dailyReportForm.ShowDialog(this);
		};
		button.Click += value;
		Button button2 = Ui.Button("FULL HISTORY", Color.FromArgb(73, 91, 117), 9);
		button2.SetBounds(218, 615, 180, 42);
		button2.Anchor = AnchorStyles.Bottom | AnchorStyles.Left;
		Controls.Add(button2);
		button2.Click += (object param0, EventArgs param1) =>
		{
			using HistoryForm historyForm = new HistoryForm();
			historyForm.ShowDialog(this);
		};
		Button button3 = Ui.Button("COPY SUMMARY", Theme.Blue, 9);
		button3.SetBounds(412, 615, 180, 42);
		button3.Anchor = AnchorStyles.Bottom | AnchorStyles.Left;
		Controls.Add(button3);
		button3.Click += (object param0, EventArgs param1) =>
		{
			try
			{
				if (!string.IsNullOrWhiteSpace(clipboardSummary))
				{
					Clipboard.SetText(clipboardSummary);
				}
				MessageBox.Show("Caregiver summary copied.", "David");
			}
			catch
			{
				MessageBox.Show("The summary could not be copied right now.", "David");
			}
		};
		Button button4 = Ui.Button("CLOSE", Color.FromArgb(88, 103, 123), 9);
		button4.SetBounds(852, 615, 180, 42);
		button4.Anchor = AnchorStyles.Bottom | AnchorStyles.Right;
		Controls.Add(button4);
		button4.Click += (object param0, EventArgs param1) =>
		{
			Close();
		};
		rangeBox.SelectedIndexChanged += (object param0, EventArgs param1) =>
		{
			RefreshInsights();
		};
		Shown += (object param0, EventArgs param1) =>
		{
			RefreshInsights();
		};
	}

	private int SelectedDays()
	{
		if (rangeBox.SelectedIndex == 1)
		{
			return 14;
		}
		if (rangeBox.SelectedIndex == 2)
		{
			return 30;
		}
		return 7;
	}

	private void RefreshInsights()
	{
		int num = SelectedDays();
		DateTime now = DateTime.Now;
		DateTime dateTime = now.Date.AddDays(-(num - 1));
		List<Reminder> list = AppData.LoadReminders().Items ?? new List<Reminder>();
		StateStore stateStore = AppData.LoadState();
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		int num2 = 0;
		int num3 = 0;
		int num4 = 0;
		int num5 = 0;
		int num6 = 0;
		int num7 = 0;
		Dictionary<string, int> dictionary = new Dictionary<string, int>(StringComparer.OrdinalIgnoreCase);
		DataTable dataTable = new DataTable();
		dataTable.Columns.Add("Day");
		dataTable.Columns.Add("Scheduled");
		dataTable.Columns.Add("Completed");
		dataTable.Columns.Add("Incomplete");
		dataTable.Columns.Add("Medication");
		dataTable.Columns.Add("I'm Okay");
		for (int i = 0; i < num; i++)
		{
			DateTime day = dateTime.AddDays(i);
			int num8 = 0;
			int num9 = 0;
			int num10 = 0;
			int num11 = 0;
			int num12 = 0;
			foreach (Reminder item in list)
			{
				DateTime? dateTime2 = Scheduler.DueForDate(item, day);
				if (!dateTime2.HasValue)
				{
					continue;
				}
				num8++;
				num2++;
				string key = Scheduler.MakeKey(item, dateTime2.Value);
				StateEntry stateEntry = stateStore.Items.FirstOrDefault((StateEntry x) => x.Key == key);
				bool flag = stateEntry != null && string.Equals(stateEntry.Status, "done", StringComparison.OrdinalIgnoreCase);
				bool flag2 = dateTime2.Value <= now;
				if (flag)
				{
					num9++;
					num3++;
				}
				else if (flag2)
				{
					num10++;
					string key2 = (string.IsNullOrWhiteSpace(item.Title) ? "Reminder" : item.Title.Trim());
					if (!dictionary.ContainsKey(key2))
					{
						dictionary[key2] = 0;
					}
					dictionary[key2]++;
				}
				if (string.Equals(item.Category, "Medication", StringComparison.OrdinalIgnoreCase))
				{
					num11++;
					num4++;
					if (flag)
					{
						num12++;
						num5++;
					}
				}
			}
			string text = ((num11 == 0) ? "—" : (num12 + "/" + num11));
			string text2 = "Off";
			if (string.Equals(securitySettings.CheckInEnabled, "on", StringComparison.OrdinalIgnoreCase))
			{
				DateTime dateTime3 = DailySupport.TimeToday(securitySettings.CheckInTime, day, 12);
				DateTime dateTime4 = dateTime3.AddMinutes((securitySettings.CheckInGraceMinutes <= 0) ? 30 : securitySettings.CheckInGraceMinutes);
				if (dateTime3 <= now)
				{
					num6++;
					StateEntry stateEntry2 = stateStore.Items.FirstOrDefault((StateEntry x) => x.Key == DailySupport.CheckInStateKey(day));
					if (stateEntry2 == null || !string.Equals(stateEntry2.Status, "done", StringComparison.OrdinalIgnoreCase))
					{
						if (stateEntry2 == null || !string.Equals(stateEntry2.Status, "missed", StringComparison.OrdinalIgnoreCase))
						{
							text2 = ((!(now >= dateTime4)) ? "Waiting" : "No response");
						}
						else
						{
							text2 = "Missed";
						}
					}
					else
					{
						num7++;
						text2 = "OK";
					}
				}
				else
				{
					text2 = "Later";
				}
			}
			dataTable.Rows.Add(day.ToString("ddd MMM d"), num8.ToString(), num9.ToString(), num10.ToString(), text, text2);
		}
		int num13 = ((num2 == 0) ? 100 : ((int)Math.Round((double)num3 * 100.0 / (double)num2)));
		completion.Text = ((num2 == 0) ? "No reminders" : (num13 + "%  •  " + num3 + "/" + num2));
		int num14 = ((num4 == 0) ? 100 : ((int)Math.Round((double)num5 * 100.0 / (double)num4)));
		medication.Text = ((num4 == 0) ? "No medication" : (num14 + "%  •  " + num5 + "/" + num4));
		int num15 = ((num6 == 0) ? 100 : ((int)Math.Round((double)num7 * 100.0 / (double)num6)));
		checkins.Text = ((num6 == 0) ? "Not scheduled" : (num15 + "%  •  " + num7 + "/" + num6));
		List<KeyValuePair<string, int>> list2 = dictionary.OrderByDescending((KeyValuePair<string, int> x) =>
		{
			KeyValuePair<string, int> keyValuePair = x;
			return keyValuePair.Value;
		}).ThenBy((KeyValuePair<string, int> x) =>
		{
			KeyValuePair<string, int> keyValuePair = x;
			return keyValuePair.Key;
		}).Take(2)
			.ToList();
		pattern.Text = ((list2.Count == 0) ? "None" : string.Join("  •  ", list2.Select((KeyValuePair<string, int> x) => x.Key + " (" + x.Value + ")").ToArray()));
		grid.DataSource = dataTable;
		string text3;
		if (string.IsNullOrWhiteSpace(securitySettings.PersonPreferredName))
		{
			text3 = (string.IsNullOrWhiteSpace(securitySettings.PersonFullName) ? "David" : securitySettings.PersonFullName);
		}
		else
		{
			text3 = securitySettings.PersonPreferredName;
		}
		clipboardSummary = text3 + " caregiver insights — last " + num + " days\r\nReminders: " + ((num2 == 0) ? "none scheduled" : (num3 + " of " + num2 + " completed (" + num13 + "%)")) + "\r\nMedication: " + ((num4 == 0) ? "none scheduled" : (num5 + " of " + num4 + " completed (" + num14 + "%)")) + "\r\nI'm Okay: " + ((num6 == 0) ? "not scheduled" : (num7 + " of " + num6 + " completed (" + num15 + "%)")) + "\r\nMost often incomplete: " + ((list2.Count == 0) ? "none" : string.Join(", ", list2.Select((KeyValuePair<string, int> x) => x.Key + " (" + x.Value + ")").ToArray())) + "\r\nActivity summary only; not a medical assessment.";
	}
}
