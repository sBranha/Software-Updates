using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Windows.Forms;

namespace DavidCompanion;

public class RecoveryForm : Form
{
	private ListBox backups = new ListBox();

	private Label status = new Label();

	private List<string> paths = new List<string>();

	public RecoveryForm()
	{
		Text = "David 5.9 - Backup & Recovery";
		StartPosition = FormStartPosition.CenterParent;
		Size = new Size(800, 650);
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		Label label = Ui.Label("Backup & Recovery", 22, bold: true, Theme.Text);
		label.SetBounds(26, 20, 520, 42);
		Controls.Add(label);
		Label label2 = Ui.Label("David automatically keeps daily local backups when enabled. Restoring a backup first creates a safety backup of the current data.", 10, bold: false, Theme.Muted);
		label2.SetBounds(29, 66, 700, 48);
		Controls.Add(label2);
		backups.SetBounds(28, 128, 724, 300);
		backups.Font = new Font("Segoe UI", 10f);
		Controls.Add(backups);
		status = Ui.Label("", 9, bold: false, Theme.Muted);
		status.SetBounds(30, 440, 700, 38);
		Controls.Add(status);
		Button button = Ui.Button("REFRESH", Color.FromArgb(73, 91, 117), 9);
		button.SetBounds(28, 500, 140, 46);
		Controls.Add(button);
		EventHandler value = (object param0, EventArgs param1) =>
		{
			LoadBackups();
		};
		button.Click += value;
		Button button2 = Ui.Button("OPEN BACKUP FOLDER", Theme.Blue, 9);
		button2.SetBounds(184, 500, 210, 46);
		Controls.Add(button2);
		button2.Click += (object param0, EventArgs param1) =>
		{
			try
			{
				Directory.CreateDirectory(AppData.BackupsRoot);
				Process.Start(AppData.BackupsRoot);
			}
			catch
			{
			}
		};
		Button button3 = Ui.Button("RESTORE SELECTED", Theme.Green, 10);
		button3.SetBounds(410, 500, 210, 46);
		Controls.Add(button3);
		button3.Click += (object param0, EventArgs param1) =>
		{
			RestoreSelected();
		};
		Button button4 = Ui.Button("CHOOSE OTHER", Color.FromArgb(97, 82, 115), 9);
		button4.SetBounds(636, 500, 116, 46);
		Controls.Add(button4);
		button4.Click += (object param0, EventArgs param1) =>
		{
			ChooseOther();
		};
		Shown += (object param0, EventArgs param1) =>
		{
			LoadBackups();
		};
	}

	private void LoadBackups()
	{
		paths = AppData.GetBackupFolders();
		backups.Items.Clear();
		foreach (string path in paths)
		{
			backups.Items.Add(Path.GetFileName(path));
		}
		bool flag = AppData.ValidateData(out var detail);
		status.Text = (flag ? "CURRENT DATA OK — " : "CURRENT DATA WARNING — ") + detail;
	}

	private void RestoreSelected()
	{
		if (backups.SelectedIndex < 0 || backups.SelectedIndex >= paths.Count)
		{
			MessageBox.Show("Select a backup first.", "David");
		}
		else
		{
			RestorePath(paths[backups.SelectedIndex]);
		}
	}

	private void ChooseOther()
	{
		using FolderBrowserDialog folderBrowserDialog = new FolderBrowserDialog();
		folderBrowserDialog.Description = "Choose a David backup folder";
		if (folderBrowserDialog.ShowDialog(this) == DialogResult.OK)
		{
			RestorePath(folderBrowserDialog.SelectedPath);
		}
	}

	private void RestorePath(string path)
	{
		if (MessageBox.Show("Restore David's reminders, history, settings, PIN, and local pictures from this backup?\r\n\r\nA safety backup of the current data will be created first.", "David - Restore", MessageBoxButtons.YesNo, MessageBoxIcon.Exclamation) == DialogResult.Yes)
		{
			if (!AppData.RestoreBackup(path, out var error))
			{
				MessageBox.Show("Restore failed.\r\n\r\n" + error, "David", MessageBoxButtons.OK, MessageBoxIcon.Hand);
				return;
			}
			MessageBox.Show("Backup restored. Close and reopen David so every screen reloads the restored settings and reminders.", "David", MessageBoxButtons.OK, MessageBoxIcon.Asterisk);
			LoadBackups();
		}
	}
}
