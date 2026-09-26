using System;
using System.Drawing;
using System.Windows.Forms;

namespace DavidCompanion;

public class LicenseInfoForm : Form
{
	private Label body = new Label();

	private Button verify = new Button();

	public LicenseInfoForm()
	{
		Text = "David - License";
		StartPosition = FormStartPosition.CenterParent;
		Size = new Size(720, 500);
		FormBorderStyle = FormBorderStyle.FixedDialog;
		MaximizeBox = false;
		MinimizeBox = false;
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		Label label = Ui.Label("CAREGIVER ONLY", 9, bold: true, Theme.Gold);
		label.SetBounds(28, 22, 220, 24);
		Controls.Add(label);
		Label label2 = Ui.Label("David License", 24, bold: true, Theme.Text);
		label2.SetBounds(26, 54, 600, 44);
		Controls.Add(label2);
		body = Ui.Label("", 11, bold: false, Theme.Text);
		body.SetBounds(30, 120, 650, 205);
		Controls.Add(body);
		verify = Ui.Button("CHECK LICENSE NOW", Theme.Green, 10);
		verify.SetBounds(30, 355, 250, 50);
		Controls.Add(verify);
		Button button = verify;
		EventHandler value = (object param0, EventArgs param1) =>
		{
			Cursor = Cursors.WaitCursor;
			bool flag = LicenseService.ValidateIfDue(force: true, out var message);
			Cursor = Cursors.Default;
			RefreshView();
			MessageBox.Show(message, "David License", MessageBoxButtons.OK, flag ? MessageBoxIcon.Asterisk : MessageBoxIcon.Exclamation);
		};
		button.Click += value;
		Button button2 = Ui.Button("CLOSE", Color.FromArgb(73, 91, 117), 10);
		button2.SetBounds(500, 355, 180, 50);
		Controls.Add(button2);
		button2.Click += (object param0, EventArgs param1) =>
		{
			Close();
		};
		Shown += (object param0, EventArgs param1) =>
		{
			RefreshView();
		};
	}

	private void RefreshView()
	{
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		string text;
		if (string.Equals(securitySettings.LicenseNeverExpires, "yes", StringComparison.OrdinalIgnoreCase))
		{
			text = "NEVER EXPIRES";
		}
		else
		{
			text = (string.IsNullOrWhiteSpace(securitySettings.LicenseExpiresAt) ? "No expiration recorded" : ("Expires " + securitySettings.LicenseExpiresAt));
		}
		string text2 = "Not yet";
		if (DateTime.TryParse(securitySettings.LicenseLastValidatedUtc, out var result))
		{
			text2 = result.ToLocalTime().ToString("g");
		}
		body.Text = "Status: " + (LicenseService.IsLocallyActive() ? "ACTIVE" : "ACTIVATION REQUIRED") + "\r\nLicense: " + LicenseService.TypeLabel(securitySettings) + "\r\nLicense ID: " + (string.IsNullOrWhiteSpace(securitySettings.LicensePublicId) ? "Not activated" : securitySettings.LicensePublicId) + "\r\nLicensed to: " + (string.IsNullOrWhiteSpace(securitySettings.LicenseCustomerName) ? "Not recorded" : securitySettings.LicenseCustomerName) + "\r\nTerm: " + text + "\r\nConnected Care: " + (string.IsNullOrWhiteSpace(securitySettings.LicenseCloudPlan) ? "Current service rules apply" : securitySettings.LicenseCloudPlan) + "\r\nLast server verification: " + text2;
	}
}
