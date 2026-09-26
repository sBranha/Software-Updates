using System;
using System.Diagnostics;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;

namespace DavidCompanion;

public class FirstRunSetupForm : Form
{
	private TextBox code = new TextBox();

	private Label status = new Label();

	public FirstRunSetupForm()
	{
		Text = "David 6.1.6 - Pair Device";
		StartPosition = FormStartPosition.CenterScreen;
		Size = new Size(650, 520);
		FormBorderStyle = FormBorderStyle.FixedDialog;
		MaximizeBox = false;
		MinimizeBox = false;
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		Label label = Ui.Label("DAVID 6.1.6 CONNECTED CARE", 9, bold: true, Theme.Gold);
		label.SetBounds(28, 22, 250, 24);
		Controls.Add(label);
		Label label2 = Ui.Label("Pair this Windows computer", 23, bold: true, Theme.Text);
		label2.SetBounds(26, 52, 560, 48);
		Controls.Add(label2);
		Label label3 = Ui.Label("Your David license is active. Now pair this Windows PC to the person: on the caregiver website open the person, choose Devices → Add Device, then enter that 6-digit code below.", 11, bold: false, Theme.Muted);
		label3.SetBounds(29, 108, 575, 88);
		Controls.Add(label3);
		Label label4 = Ui.Label("6-DIGIT ADD DEVICE CODE", 9, bold: true, Theme.Blue);
		label4.SetBounds(30, 216, 220, 24);
		Controls.Add(label4);
		code.SetBounds(30, 246, 570, 54);
		code.Font = new Font("Segoe UI", 24f, FontStyle.Bold);
		code.TextAlign = HorizontalAlignment.Center;
		code.MaxLength = 6;
		Controls.Add(code);
		status = Ui.Label("This temporary code links this Windows PC to the same person, reminders, Chat, HELP, and completion history as the phone.", 9, bold: false, Theme.Muted);
		status.SetBounds(31, 310, 570, 52);
		Controls.Add(status);
		Button button = Ui.Button("PAIR THIS DEVICE", Theme.Green, 11);
		button.SetBounds(30, 382, 260, 50);
		Controls.Add(button);
		Button button2 = Ui.Button("PAIR LATER", Color.FromArgb(74, 91, 116), 10);
		button2.SetBounds(310, 382, 180, 50);
		Controls.Add(button2);
		Button button3 = Ui.Button("OPEN WEBSITE", Theme.Blue, 9);
		button3.SetBounds(504, 382, 100, 50);
		Controls.Add(button3);
		EventHandler value = (object param0, EventArgs param1) =>
		{
			string text = new string(code.Text.Where(char.IsDigit).ToArray());
			if (text.Length != 6)
			{
				MessageBox.Show("Enter the six-digit setup code.", "David");
			}
			else
			{
				Cursor = Cursors.WaitCursor;
				CloudResponse cloudResponse = CloudClient.ClaimSetupCode(text);
				Cursor = Cursors.Default;
				if (cloudResponse != null && cloudResponse.ok)
				{
					MessageBox.Show("This Windows PC is linked to the same David person and caregiver account.", "David", MessageBoxButtons.OK, MessageBoxIcon.Asterisk);
					DialogResult = DialogResult.OK;
					Close();
				}
				else
				{
					MessageBox.Show((cloudResponse == null) ? "David could not connect." : cloudResponse.error, "David", MessageBoxButtons.OK, MessageBoxIcon.Exclamation);
				}
			}
		};
		button.Click += value;
		button2.Click += (object param0, EventArgs param1) =>
		{
			SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
			securitySettings.SetupCompletedPreference = "local";
			AppData.SaveSettings(securitySettings);
			DialogResult = DialogResult.Ignore;
			Close();
		};
		button3.Click += (object param0, EventArgs param1) =>
		{
			try
			{
				Process.Start(new ProcessStartInfo
				{
					FileName = "https://david.forgegather.net",
					UseShellExecute = true
				});
			}
			catch
			{
			}
		};
		Shown += (object param0, EventArgs param1) =>
		{
			code.Focus();
		};
	}
}
