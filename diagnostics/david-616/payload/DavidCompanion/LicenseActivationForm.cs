using System;
using System.Diagnostics;
using System.Drawing;
using System.Windows.Forms;

namespace DavidCompanion;

public class LicenseActivationForm : Form
{
	private TextBox code = new TextBox();

	private Label status = new Label();

	private Button activate = new Button();

	public LicenseActivationForm()
	{
		Text = "Activate David";
		StartPosition = FormStartPosition.CenterScreen;
		Size = new Size(720, 520);
		FormBorderStyle = FormBorderStyle.FixedDialog;
		MaximizeBox = false;
		MinimizeBox = false;
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		Label label = Ui.Label("DAVID SOFTWARE LICENSE", 9, bold: true, Theme.Gold);
		label.SetBounds(30, 24, 300, 24);
		Controls.Add(label);
		Label label2 = Ui.Label("Activate David", 27, bold: true, Theme.Text);
		label2.SetBounds(28, 58, 620, 52);
		Controls.Add(label2);
		Label label3 = Ui.Label("Enter the David license key for this person. The same license key can be used on every David device. Each device is paired separately after the license is accepted.", 11, bold: false, Theme.Muted);
		label3.SetBounds(31, 116, 635, 76);
		Controls.Add(label3);
		Label label4 = Ui.Label("12-CHARACTER LICENSE KEY", 9, bold: true, Theme.Blue);
		label4.SetBounds(31, 205, 300, 24);
		Controls.Add(label4);
		code.SetBounds(31, 234, 635, 54);
		code.Font = new Font("Segoe UI", 22f, FontStyle.Bold);
		code.TextAlign = HorizontalAlignment.Center;
		code.MaxLength = 14;
		Controls.Add(code);
		status = Ui.Label("The license belongs to the person, not this computer. After licensing, enter a 6-digit Add Device code to pair this Windows PC with the same person and caregiver account.", 9, bold: false, Theme.Muted);
		status.SetBounds(33, 301, 630, 58);
		Controls.Add(status);
		activate = Ui.Button("USE LICENSE KEY", Theme.Green, 11);
		activate.SetBounds(31, 382, 300, 54);
		Controls.Add(activate);
		Button button = Ui.Button("OPEN DAVID WEBSITE", Theme.Blue, 9);
		button.SetBounds(350, 382, 210, 54);
		Controls.Add(button);
		Button button2 = Ui.Button("CLOSE", Color.FromArgb(73, 91, 117), 9);
		button2.SetBounds(575, 382, 91, 54);
		Controls.Add(button2);
		Button button3 = activate;
		EventHandler value = (object param0, EventArgs param1) =>
		{
			DoActivate();
		};
		button3.Click += value;
		button.Click += (object param0, EventArgs param1) =>
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
		button2.Click += (object param0, EventArgs param1) =>
		{
			Close();
		};
		Shown += (object param0, EventArgs param1) =>
		{
			code.Focus();
		};
	}

	private void DoActivate()
	{
		activate.Enabled = false;
		Cursor = Cursors.WaitCursor;
		status.Text = "Activating this Windows computer…";
		DavidLicenseResponse davidLicenseResponse = LicenseService.Activate(code.Text);
		Cursor = Cursors.Default;
		activate.Enabled = true;
		if (davidLicenseResponse != null && davidLicenseResponse.ok && davidLicenseResponse.active && davidLicenseResponse.license != null)
		{
			string text = (davidLicenseResponse.license.neverExpires ? "\r\n\r\nThis license never expires." : "");
			MessageBox.Show("David license accepted. This computer can now be paired to the person.\r\n\r\n" + (davidLicenseResponse.license.typeLabel ?? "David License") + "\r\n" + davidLicenseResponse.license.publicId + text, "David - Activated", MessageBoxButtons.OK, MessageBoxIcon.Asterisk);
			DialogResult = DialogResult.OK;
			Close();
		}
		else
		{
			status.Text = ((davidLicenseResponse == null) ? "David could not activate." : (davidLicenseResponse.error ?? "David could not activate."));
			MessageBox.Show(status.Text, "David - Activation", MessageBoxButtons.OK, MessageBoxIcon.Exclamation);
		}
	}
}
