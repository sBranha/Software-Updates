using System;
using System.Drawing;
using System.Windows.Forms;

namespace DavidCompanion;

public class CaregiverToolsForm : Form
{
	public CaregiverToolsForm()
	{
		Text = "David - Caregiver Computer & Safety";
		StartPosition = FormStartPosition.CenterParent;
		Size = new Size(780, 680);
		FormBorderStyle = FormBorderStyle.FixedDialog;
		MaximizeBox = false;
		MinimizeBox = false;
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		GradientPanel gradientPanel = new GradientPanel();
		gradientPanel.SetBounds(22, 18, 720, 116);
		Controls.Add(gradientPanel);
		Label label = Ui.Label("CAREGIVER ONLY", 9, bold: true, Theme.Gold);
		label.SetBounds(26, 16, 220, 24);
		gradientPanel.Controls.Add(label);
		Label label2 = Ui.Label("Computer & Safety", 24, bold: true, Color.White);
		label2.SetBounds(24, 42, 500, 44);
		gradientPanel.Controls.Add(label2);
		Label label3 = Ui.Label("These controls are intentionally kept off David's daily screen.", 10, bold: false, Color.FromArgb(207, 220, 235));
		label3.SetBounds(27, 86, 620, 24);
		gradientPanel.Controls.Add(label3);
		RoundedPanel roundedPanel = Ui.Card(Theme.Surface);
		roundedPanel.SetBounds(22, 154, 720, 405);
		Controls.Add(roundedPanel);
		Button button = Ui.Button("TODAY STATUS", Theme.Blue, 11);
		button.SetBounds(28, 28, 310, 72);
		roundedPanel.Controls.Add(button);
		EventHandler value = (object param0, EventArgs param1) =>
		{
			using SystemStatusForm systemStatusForm = new SystemStatusForm();
			systemStatusForm.ShowDialog(this);
		};
		button.Click += value;
		Button button2 = Ui.Button("QUIET / AWAY", Color.FromArgb(92, 104, 158), 11);
		button2.SetBounds(360, 28, 330, 72);
		roundedPanel.Controls.Add(button2);
		button2.Click += (object param0, EventArgs param1) =>
		{
			using AwayStatusForm awayStatusForm = new AwayStatusForm();
			awayStatusForm.ShowDialog(this);
		};
		Button button3 = Ui.Button("HEALTH CHECK", Theme.Green, 11);
		button3.SetBounds(28, 122, 310, 72);
		roundedPanel.Controls.Add(button3);
		button3.Click += (object param0, EventArgs param1) =>
		{
			using HealthCheckForm healthCheckForm = new HealthCheckForm();
			healthCheckForm.ShowDialog(this);
		};
		Button button4 = Ui.Button("HELP GUIDE", Color.FromArgb(73, 91, 117), 11);
		button4.SetBounds(360, 122, 330, 72);
		roundedPanel.Controls.Add(button4);
		button4.Click += (object param0, EventArgs param1) =>
		{
			using HelpGuideForm helpGuideForm = new HelpGuideForm();
			helpGuideForm.ShowDialog(this);
		};
		Button button5 = Ui.Button("LICENSE", Color.FromArgb(191, 126, 42), 11);
		button5.SetBounds(28, 216, 662, 60);
		roundedPanel.Controls.Add(button5);
		button5.Click += (object param0, EventArgs param1) =>
		{
			using LicenseInfoForm licenseInfoForm = new LicenseInfoForm();
			licenseInfoForm.ShowDialog(this);
		};
		Label label4 = Ui.Label("Today Status covers power, internet, Connected Care and activity. Quiet / Away controls held reminders and return-home review. Health Check, Help Guide and License are caregiver maintenance tools.", 10, bold: false, Theme.Muted);
		label4.SetBounds(32, 292, 650, 82);
		roundedPanel.Controls.Add(label4);
		Button button6 = Ui.Button("CLOSE", Color.FromArgb(73, 91, 117), 10);
		button6.SetBounds(505, 580, 237, 48);
		Controls.Add(button6);
		button6.Click += (object param0, EventArgs param1) =>
		{
			Close();
		};
	}
}
