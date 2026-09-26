using System;
using System.Drawing;
using System.Windows.Forms;

namespace DavidCompanion;

public class WhatNextForm : Form
{
	public WhatNextForm(Occurrence next)
	{
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		string text = ((!string.IsNullOrWhiteSpace(securitySettings.PersonPreferredName)) ? securitySettings.PersonPreferredName : (string.IsNullOrWhiteSpace(securitySettings.PersonFullName) ? "David" : securitySettings.PersonFullName));
		Text = "David - What Do I Need To Do?";
		StartPosition = FormStartPosition.CenterParent;
		Size = new Size(760, 500);
		FormBorderStyle = FormBorderStyle.FixedDialog;
		MaximizeBox = false;
		MinimizeBox = false;
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		RoundedPanel roundedPanel = Ui.Card(Theme.Surface);
		roundedPanel.SetBounds(24, 24, 700, 380);
		Controls.Add(roundedPanel);
		Label label = Ui.Label("WHAT DO I NEED TO DO?", 10, bold: true, Theme.Blue);
		label.SetBounds(30, 28, 400, 28);
		roundedPanel.Controls.Add(label);
		Label label2 = Ui.Label((next == null) ? "You're all caught up." : ("Nothing right now, " + text + "."), 25, bold: true, Theme.Text);
		label2.SetBounds(28, 76, 630, 62);
		roundedPanel.Controls.Add(label2);
		Label label3 = Ui.Label((next == null) ? "There are no more reminders scheduled right now." : ("Your next thing is:\r\n\r\n" + next.Reminder.Title + "\r\n" + next.Due.ToString("h:mm tt") + (string.IsNullOrWhiteSpace(next.Reminder.Message) ? "" : ("\r\n\r\n" + next.Reminder.Message))), 14, bold: false, Theme.Muted);
		label3.SetBounds(30, 150, 630, 135);
		roundedPanel.Controls.Add(label3);
		Button button = Ui.Button("OK", Theme.Green, 12);
		button.SetBounds(210, 305, 280, 58);
		roundedPanel.Controls.Add(button);
		button.Click += (object param0, EventArgs param1) =>
		{
			Close();
		};
	}
}
