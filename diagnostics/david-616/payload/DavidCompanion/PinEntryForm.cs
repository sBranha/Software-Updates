using System;
using System.Drawing;
using System.Windows.Forms;

namespace DavidCompanion;

public class PinEntryForm : Form
{
	private TextBox pinBox = new TextBox();

	public string PinValue => pinBox.Text;

	public PinEntryForm(string reason)
	{
		Text = "David - Caregiver PIN";
		StartPosition = FormStartPosition.CenterScreen;
		Size = new Size(490, 270);
		FormBorderStyle = FormBorderStyle.FixedDialog;
		MaximizeBox = false;
		MinimizeBox = false;
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Label label = Ui.Label("Caregiver PIN required", 18, bold: true, Theme.Text);
		label.SetBounds(26, 23, 420, 38);
		Controls.Add(label);
		Label label2 = Ui.Label(reason, 10, bold: false, Theme.Muted);
		label2.SetBounds(29, 67, 420, 50);
		Controls.Add(label2);
		pinBox.SetBounds(30, 120, 410, 36);
		pinBox.Font = new Font("Segoe UI", 16f);
		pinBox.PasswordChar = '*';
		pinBox.MaxLength = 8;
		Controls.Add(pinBox);
		Button button = Ui.Button("UNLOCK", Theme.Green, 11);
		button.SetBounds(146, 178, 140, 42);
		button.DialogResult = DialogResult.OK;
		Controls.Add(button);
		Button button2 = Ui.Button("CANCEL", Color.FromArgb(91, 105, 125), 10);
		button2.SetBounds(300, 178, 140, 42);
		button2.DialogResult = DialogResult.Cancel;
		Controls.Add(button2);
		AcceptButton = button;
		CancelButton = button2;
		EventHandler value = (object param0, EventArgs param1) =>
		{
			pinBox.Focus();
		};
		Shown += value;
	}
}
