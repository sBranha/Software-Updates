using System;
using System.Drawing;
using System.Windows.Forms;

namespace DavidCompanion;

public class UpdateCenterForm : Form
{
	private Label installed = new Label();

	private Label status = new Label();

	private RichTextBox notes = new RichTextBox();

	private Button install = new Button();

	private CheckBox autoCheck = new CheckBox();

	private UpdateManifest current;

	public UpdateCenterForm()
	{
		Text = "David - Caregiver Update Center";
		StartPosition = FormStartPosition.CenterParent;
		Size = new Size(790, 650);
		FormBorderStyle = FormBorderStyle.FixedDialog;
		MaximizeBox = false;
		MinimizeBox = false;
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		GradientPanel gradientPanel = new GradientPanel();
		gradientPanel.SetBounds(22, 18, 730, 116);
		Controls.Add(gradientPanel);
		Label label = Ui.Label("CAREGIVER ONLY", 9, bold: true, Theme.Gold);
		label.SetBounds(26, 16, 220, 24);
		gradientPanel.Controls.Add(label);
		Label label2 = Ui.Label("David Update Center", 24, bold: true, Color.White);
		label2.SetBounds(24, 42, 500, 44);
		gradientPanel.Controls.Add(label2);
		Label label3 = Ui.Label("Checks the trusted David server, verifies the download, backs up, then updates safely.", 10, bold: false, Color.FromArgb(207, 220, 235));
		label3.SetBounds(27, 86, 665, 24);
		gradientPanel.Controls.Add(label3);
		RoundedPanel roundedPanel = Ui.Card(Theme.Surface);
		roundedPanel.SetBounds(22, 154, 730, 370);
		Controls.Add(roundedPanel);
		installed = Ui.Label("Installed: 6.1.6", 12, bold: true, Theme.Text);
		installed.SetBounds(28, 20, 330, 30);
		roundedPanel.Controls.Add(installed);
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		autoCheck.Text = "Automatically check for updates";
		autoCheck.ForeColor = Theme.Text;
		autoCheck.SetBounds(395, 20, 285, 30);
		autoCheck.Checked = !string.Equals(securitySettings.UpdateAutoCheckEnabled, "off", StringComparison.OrdinalIgnoreCase);
		roundedPanel.Controls.Add(autoCheck);
		autoCheck.CheckedChanged += (object param0, EventArgs param1) =>
		{
			SecuritySettings securitySettings2 = AppData.LoadSettings() ?? new SecuritySettings();
			securitySettings2.UpdateAutoCheckEnabled = (autoCheck.Checked ? "on" : "off");
			AppData.SaveSettings(securitySettings2);
		};
		status = Ui.Label("Checking for updates…", 13, bold: true, Theme.Gold);
		status.SetBounds(28, 62, 650, 34);
		roundedPanel.Controls.Add(status);
		Label label4 = Ui.Label("WHAT'S NEW", 8, bold: true, Theme.Blue);
		label4.SetBounds(28, 112, 180, 22);
		roundedPanel.Controls.Add(label4);
		notes.SetBounds(28, 140, 674, 135);
		notes.ReadOnly = true;
		notes.BackColor = Theme.Surface3;
		notes.ForeColor = Theme.Text;
		notes.BorderStyle = BorderStyle.FixedSingle;
		notes.Font = new Font("Segoe UI", 10f);
		roundedPanel.Controls.Add(notes);
		Label label5 = Ui.Label("Safety: David checks the official public GitHub Software-Updates repository, verifies the published SHA-256, creates a fresh local backup, and launches a normal prebuilt Windows Setup.exe.", 9, bold: false, Theme.Muted);
		label5.SetBounds(28, 291, 674, 68);
		roundedPanel.Controls.Add(label5);
		Button button = Ui.Button("CHECK NOW", Theme.Blue, 10);
		button.SetBounds(22, 545, 190, 50);
		Controls.Add(button);
		button.Click += (object param0, EventArgs param1) =>
		{
			CheckNow();
		};
		install = Ui.Button("INSTALL UPDATE", Theme.Green, 10);
		install.SetBounds(228, 545, 260, 50);
		install.Enabled = false;
		Controls.Add(install);
		install.Click += (object param0, EventArgs param1) =>
		{
			InstallNow();
		};
		Button button2 = Ui.Button("CLOSE", Color.FromArgb(73, 91, 117), 10);
		button2.SetBounds(505, 545, 247, 50);
		Controls.Add(button2);
		button2.Click += (object param0, EventArgs param1) =>
		{
			Close();
		};
		Shown += (object param0, EventArgs param1) =>
		{
			CheckNow();
		};
	}

	private void CheckNow()
	{
		status.Text = "Checking for updates…";
		status.ForeColor = Theme.Gold;
		install.Enabled = false;
		notes.Text = "";
		Cursor = Cursors.WaitCursor;
		bool flag = UpdateService.Check(out var manifest, out var error);
		Cursor = Cursors.Default;
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		securitySettings.UpdateLastCheckUtc = DateTime.UtcNow.ToString("o");
		AppData.SaveSettings(securitySettings);
		if (!flag)
		{
			current = null;
			status.Text = "Could not check for updates";
			status.ForeColor = Theme.Danger;
			notes.Text = error;
			return;
		}
		current = manifest;
		notes.Text = (string.IsNullOrWhiteSpace(manifest.notes) ? "No release notes were supplied." : manifest.notes);
		if (UpdateService.IsNewer(manifest.version, "6.1.6"))
		{
			status.Text = "UPDATE AVAILABLE — DAVID " + manifest.version;
			status.ForeColor = Theme.Green;
			install.Enabled = true;
			UpdateService.NotifyLinkedCaregiver(manifest);
		}
		else
		{
			status.Text = "YOU ARE UP TO DATE — DAVID 6.1.6";
			status.ForeColor = Theme.Green;
			install.Enabled = false;
		}
	}

	private void InstallNow()
	{
		if (current != null && UpdateService.IsNewer(current.version, "6.1.6") && MessageBox.Show("Install David " + current.version + " now?\r\n\r\nDavid will download and verify the update, make a fresh backup, close for a moment, install the new version, and reopen automatically.", "David - Install Update", MessageBoxButtons.YesNo, MessageBoxIcon.Question) == DialogResult.Yes)
		{
			install.Enabled = false;
			Cursor = Cursors.WaitCursor;
			bool flag = UpdateService.PrepareAndStart(current, out var error, out var backupFolder);
			Cursor = Cursors.Default;
			if (!flag)
			{
				install.Enabled = true;
				MessageBox.Show("The update was NOT installed.\r\n\r\n" + error + "\r\n\r\nYour working David program was not intentionally replaced.", "David - Update Stopped", MessageBoxButtons.OK, MessageBoxIcon.Exclamation);
			}
			else
			{
				MessageBox.Show("Safety backup created:\r\n" + backupFolder + "\r\n\r\nDavid will now close, apply the verified update, and reopen. If the new program does not stay running, the last-known-good program will be restored automatically.", "David - Updating", MessageBoxButtons.OK, MessageBoxIcon.Asterisk);
				Application.Exit();
			}
		}
	}
}
