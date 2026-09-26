using System;
using System.Drawing;
using System.Windows.Forms;

namespace DavidCompanion;

public class CaregiverHomeMessageForm : Form
{
	private string messageId;

	private string from;

	private string body;

	private object speech;

	public CaregiverHomeMessageForm(string id, string sender, string text)
	{
		messageId = id ?? "";
		from = (string.IsNullOrWhiteSpace(sender) ? "Caregiver" : sender);
		body = text ?? "";
		Text = "David - Message from " + from;
		StartPosition = FormStartPosition.CenterParent;
		Size = new Size(820, 610);
		FormBorderStyle = FormBorderStyle.FixedDialog;
		MaximizeBox = false;
		MinimizeBox = false;
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		RoundedPanel roundedPanel = Ui.Card(Theme.Surface);
		roundedPanel.SetBounds(24, 24, 754, 470);
		Controls.Add(roundedPanel);
		Label label = Ui.Label("MESSAGE FROM " + from.ToUpperInvariant(), 10, bold: true, Theme.Gold);
		label.SetBounds(30, 28, 650, 28);
		roundedPanel.Controls.Add(label);
		Label label2 = Ui.Label("Your caregiver sent you a message", 24, bold: true, Theme.Text);
		label2.SetBounds(28, 70, 680, 55);
		roundedPanel.Controls.Add(label2);
		Label label3 = Ui.Label(body, 17, bold: false, Theme.Text);
		label3.SetBounds(30, 145, 690, 190);
		roundedPanel.Controls.Add(label3);
		Button button = Ui.Button("READ IT ALOUD", Theme.Blue, 10);
		button.SetBounds(30, 365, 210, 58);
		roundedPanel.Controls.Add(button);
		button.Click += (object param0, EventArgs param1) =>
		{
			SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
			VoiceService.Stop(speech);
			speech = VoiceService.Speak(from + " says: " + body, securitySettings.VoiceName, securitySettings.VoiceRate, securitySettings.VoiceVolume);
		};
		Button button2 = Ui.Button("OPEN CHAT", Color.FromArgb(73, 91, 117), 10);
		button2.SetBounds(258, 365, 190, 58);
		roundedPanel.Controls.Add(button2);
		button2.Click += (object param0, EventArgs param1) =>
		{
			using ChatForm chatForm = new ChatForm();
			chatForm.ShowDialog(this);
		};
		Button button3 = Ui.Button("OKAY", Theme.Green, 12);
		button3.SetBounds(466, 365, 255, 58);
		roundedPanel.Controls.Add(button3);
		button3.Click += (object param0, EventArgs param1) =>
		{
			SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
			securitySettings.HomeMessageDismissedId = messageId;
			AppData.SaveSettings(securitySettings);
			Close();
		};
		FormClosed += (object param0, FormClosedEventArgs param1) =>
		{
			VoiceService.Stop(speech);
		};
	}
}
