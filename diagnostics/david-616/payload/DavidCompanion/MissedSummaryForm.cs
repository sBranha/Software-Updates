using System;
using System.Collections.Generic;
using System.Drawing;
using System.Windows.Forms;

namespace DavidCompanion;

public class MissedSummaryForm : Form
{
	private DateTime day;

	public MissedSummaryForm(DateTime forDay)
	{
		day = forDay;
		List<AlertWorkItem> missedWork = DailySummary.GetMissedWork(day);
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		string text = ((!string.IsNullOrWhiteSpace(securitySettings.PersonPreferredName)) ? securitySettings.PersonPreferredName : (string.IsNullOrWhiteSpace(securitySettings.PersonFullName) ? "David" : securitySettings.PersonFullName));
		Text = "David - Missed Items";
		StartPosition = FormStartPosition.CenterScreen;
		Size = new Size(820, 650);
		FormBorderStyle = FormBorderStyle.FixedDialog;
		MaximizeBox = false;
		MinimizeBox = false;
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		TopMost = true;
		RoundedPanel roundedPanel = Ui.Card(Theme.Surface);
		roundedPanel.SetBounds(24, 24, 756, 530);
		Controls.Add(roundedPanel);
		Label label = Ui.Label("MISSED ITEMS SUMMARY", 9, bold: true, Theme.Gold);
		label.SetBounds(28, 24, 300, 24);
		roundedPanel.Controls.Add(label);
		Label label2 = Ui.Label((missedWork.Count == 0) ? "You're caught up." : (text + ", you missed " + missedWork.Count + " thing" + ((missedWork.Count == 1) ? "" : "s") + "."), 24, bold: true, Theme.Text);
		label2.SetBounds(26, 58, 690, 56);
		roundedPanel.Controls.Add(label2);
		Label label3 = Ui.Label((missedWork.Count == 0) ? "There is nothing waiting in your missed-items summary." : "These non-critical reminders were put aside instead of opening a pile of old windows. You can go through them together now.", 11, bold: false, Theme.Muted);
		label3.SetBounds(29, 120, 685, 62);
		roundedPanel.Controls.Add(label3);
		FlowLayoutPanel flowLayoutPanel = new FlowLayoutPanel();
		flowLayoutPanel.SetBounds(28, 194, 698, 222);
		flowLayoutPanel.AutoScroll = true;
		flowLayoutPanel.FlowDirection = FlowDirection.TopDown;
		flowLayoutPanel.WrapContents = false;
		flowLayoutPanel.BackColor = Theme.Surface;
		roundedPanel.Controls.Add(flowLayoutPanel);
		foreach (AlertWorkItem item in missedWork)
		{
			RoundedPanel roundedPanel2 = Ui.Card(Theme.Surface2);
			roundedPanel2.Width = 650;
			roundedPanel2.Height = 58;
			Label label4 = Ui.Label(item.Reminder.Title ?? "Reminder", 11, bold: true, Theme.Text);
			label4.SetBounds(14, 8, 450, 24);
			roundedPanel2.Controls.Add(label4);
			Label label5 = Ui.Label(item.Scheduled.ToString("h:mm tt") + "  •  " + (item.Reminder.Category ?? "Other"), 8, bold: false, Theme.Muted);
			label5.SetBounds(14, 32, 430, 20);
			roundedPanel2.Controls.Add(label5);
			flowLayoutPanel.Controls.Add(roundedPanel2);
		}
		Button button = Ui.Button("GO THROUGH THESE NOW", Theme.Green, 11);
		button.SetBounds(28, 446, 330, 58);
		button.Enabled = missedWork.Count > 0;
		roundedPanel.Controls.Add(button);
		button.Click += (object param0, EventArgs param1) =>
		{
			Hide();
			using (ReminderBatchForm reminderBatchForm = new ReminderBatchForm(DailySummary.GetMissedWork(DateTime.Now)))
			{
				reminderBatchForm.ShowDialog();
			}
			Close();
		};
		Button button2 = Ui.Button("NOT NOW", Color.FromArgb(73, 91, 117), 10);
		button2.SetBounds(378, 446, 348, 58);
		roundedPanel.Controls.Add(button2);
		button2.Click += (object param0, EventArgs param1) =>
		{
			Close();
		};
	}
}
