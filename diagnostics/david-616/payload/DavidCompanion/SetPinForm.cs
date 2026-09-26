using System;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;

namespace DavidCompanion;

public class SetPinForm : Form
{
	private TextBox pin1 = new TextBox();

	private TextBox pin2 = new TextBox();

	public string PinValue { get; private set; }

	public SetPinForm(bool changing)
	{
		Text = (changing ? "David - Change Caregiver PIN" : "David - Create Caregiver PIN");
		StartPosition = FormStartPosition.CenterScreen;
		Size = new Size(540, 355);
		FormBorderStyle = FormBorderStyle.FixedDialog;
		MaximizeBox = false;
		MinimizeBox = false;
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Label label = Ui.Label(changing ? "Choose a new caregiver PIN" : "Create a caregiver PIN", 18, bold: true, Theme.Text);
		label.SetBounds(25, 22, 470, 40);
		Controls.Add(label);
		Label label2 = Ui.Label("Use 4 to 8 digits. This protects caregiver editing, stopping David, startup settings, and normal uninstall.", 10, bold: false, Theme.Muted);
		label2.SetBounds(28, 67, 460, 52);
		Controls.Add(label2);
		Label label3 = Ui.Label("PIN", 10, bold: true, Theme.Text);
		label3.SetBounds(30, 132, 105, 28);
		Controls.Add(label3);
		pin1.SetBounds(145, 126, 340, 34);
		pin1.Font = new Font("Segoe UI", 15f);
		pin1.PasswordChar = '*';
		pin1.MaxLength = 8;
		Controls.Add(pin1);
		Label label4 = Ui.Label("Confirm PIN", 10, bold: true, Theme.Text);
		label4.SetBounds(30, 180, 105, 28);
		Controls.Add(label4);
		pin2.SetBounds(145, 174, 340, 34);
		pin2.Font = new Font("Segoe UI", 15f);
		pin2.PasswordChar = '*';
		pin2.MaxLength = 8;
		Controls.Add(pin2);
		Button button = Ui.Button("SAVE PIN", Theme.Green, 11);
		button.SetBounds(193, 238, 140, 45);
		Controls.Add(button);
		Button button2 = Ui.Button("CANCEL", Color.FromArgb(91, 105, 125), 10);
		button2.SetBounds(345, 238, 140, 45);
		button2.DialogResult = DialogResult.Cancel;
		Controls.Add(button2);
		button.Click += (object param0, EventArgs param1) =>
		{
			string text = pin1.Text.Trim();
			if (text.Length < 4 || text.Length > 8 || text.Any((char c) => !char.IsDigit(c)))
			{
				MessageBox.Show("The PIN must be 4 to 8 numbers.", "David");
			}
			else if (text != pin2.Text.Trim())
			{
				MessageBox.Show("The two PIN entries do not match.", "David");
			}
			else
			{
				PinValue = text;
				DialogResult = DialogResult.OK;
				Close();
			}
		};
		AcceptButton = button;
		CancelButton = button2;
		Shown += (object param0, EventArgs param1) =>
		{
			pin1.Focus();
		};
	}
}
