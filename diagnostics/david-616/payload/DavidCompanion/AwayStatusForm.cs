using System;
using System.Drawing;
using System.Windows.Forms;

namespace DavidCompanion;

public class AwayStatusForm : Form
{
	public AwayStatusForm()
	{
		SecuritySettings s = AppData.LoadSettings() ?? new SecuritySettings();
		QuietAwayService.ExpireAwayIfNeeded(s, DateTime.Now);
		s = AppData.LoadSettings() ?? new SecuritySettings();
		bool flag = QuietAwayService.AwayActive(s, DateTime.Now);
		bool flag2 = QuietAwayService.QuietHoursActive(s, DateTime.Now);
		int held = QuietAwayService.AwayHeldCount();
		DateTime.TryParse(s.AwayModeUntil, out var result);
		Text = "David - Quiet & Away";
		StartPosition = FormStartPosition.CenterParent;
		Size = new Size(780, 560);
		FormBorderStyle = FormBorderStyle.FixedDialog;
		MaximizeBox = false;
		MinimizeBox = false;
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		RoundedPanel roundedPanel = Ui.Card(Theme.Surface);
		roundedPanel.SetBounds(24, 24, 716, 430);
		Controls.Add(roundedPanel);
		Label label = Ui.Label("QUIET HOURS & AWAY MODE", 9, bold: true, Theme.Gold);
		label.SetBounds(28, 26, 360, 24);
		roundedPanel.Controls.Add(label);
		Label label2 = Ui.Label(flag ? "Away Mode is on." : (flag2 ? "Quiet Hours are active." : ((held > 0) ? "There are held reminders to review." : "David is in normal reminder mode.")), 23, bold: true, Theme.Text);
		label2.SetBounds(27, 64, 650, 52);
		roundedPanel.Controls.Add(label2);
		Label label3 = Ui.Label((flag ? ("Ordinary reminders are being saved until you are home. Away Mode is scheduled until " + result.ToString("dddd, MMM d at h:mm tt") + (string.IsNullOrWhiteSpace(s.AwayModeNote) ? "." : (".\r\n\r\nCaregiver note: " + s.AwayModeNote))) : (flag2 ? ("Ordinary reminders are being held until " + QuietAwayService.QuietHoursRelease(s, DateTime.Now).ToString("h:mm tt") + ".") : "Quiet Hours and Away Mode can be scheduled from Caregiver Settings.")) + "\r\n\r\nMedication, Appointment, Important, Critical, and Repeat Until Done reminders are never silenced by these modes.", 11, bold: false, Theme.Muted);
		label3.SetBounds(30, 124, 650, 130);
		roundedPanel.Controls.Add(label3);
		Label label4 = Ui.Label((held == 0) ? "No Away Mode reminders are waiting." : (held + " reminder" + ((held == 1) ? " is" : "s are") + " waiting from Away Mode."), 11, bold: true, (held > 0) ? Theme.Gold : Theme.Green);
		label4.SetBounds(30, 270, 630, 30);
		roundedPanel.Controls.Add(label4);
		Button button = Ui.Button("I'M HOME NOW", Theme.Green, 11);
		button.SetBounds(28, 328, 220, 58);
		button.Enabled = flag;
		roundedPanel.Controls.Add(button);
		button.Click += (object param0, EventArgs param1) =>
		{
			QuietAwayService.EndAwayMode();
			if (((held > 0) ? MessageBox.Show("Away Mode is off. Review the reminders that were held while away?", "David", MessageBoxButtons.YesNo, MessageBoxIcon.Question) : DialogResult.No) == DialogResult.Yes)
			{
				Hide();
				using ReminderBatchForm reminderBatchForm = new ReminderBatchForm(QuietAwayService.GetAwayHeldWork());
				reminderBatchForm.ShowDialog();
			}
			Close();
		};
		Button button2 = Ui.Button((held > 0) ? "REVIEW HELD ITEMS" : "NO HELD ITEMS", (held > 0) ? Theme.Gold : Color.FromArgb(73, 91, 117), 10);
		button2.SetBounds(266, 328, 250, 58);
		button2.Enabled = held > 0;
		roundedPanel.Controls.Add(button2);
		button2.Click += (object param0, EventArgs param1) =>
		{
			Hide();
			using (ReminderBatchForm reminderBatchForm = new ReminderBatchForm(QuietAwayService.GetAwayHeldWork()))
			{
				reminderBatchForm.ShowDialog();
			}
			Close();
		};
		Button button3 = Ui.Button("CLOSE", Color.FromArgb(73, 91, 117), 10);
		button3.SetBounds(534, 328, 150, 58);
		roundedPanel.Controls.Add(button3);
		button3.Click += (object param0, EventArgs param1) =>
		{
			Close();
		};
	}
}
