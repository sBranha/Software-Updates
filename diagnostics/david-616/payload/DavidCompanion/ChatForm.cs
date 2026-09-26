using System;
using System.Collections.Generic;
using System.Drawing;
using System.Globalization;
using System.Linq;
using System.Threading;
using System.Windows.Forms;

namespace DavidCompanion;

public class ChatForm : Form
{
	private FlowLayoutPanel messages = new FlowLayoutPanel();

	private TextBox custom = new TextBox();

	private Label status = new Label();

	private Label unreadHint = new Label();

	private System.Windows.Forms.Timer timer = new System.Windows.Forms.Timer();

	private bool polling;

	private string lastRendered = "";

	private string replyTargetId = "";

	public ChatForm()
	{
		Text = "David - Chat Room";
		StartPosition = FormStartPosition.CenterScreen;
		Size = new Size(820, 720);
		MinimumSize = new Size(760, 640);
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		GradientPanel gradientPanel = new GradientPanel();
		gradientPanel.SetBounds(18, 16, 768, 112);
		gradientPanel.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
		Controls.Add(gradientPanel);
		Label label = Ui.Label("DAVID CHAT", 9, bold: true, Color.FromArgb(239, 184, 92));
		label.SetBounds(24, 17, 180, 23);
		gradientPanel.Controls.Add(label);
		Label label2 = Ui.Label("Conversation", 24, bold: true, Color.White);
		label2.SetBounds(22, 39, 470, 43);
		gradientPanel.Controls.Add(label2);
		Label label3 = Ui.Label("Private, ongoing chat with your caregivers", 10, bold: false, Color.FromArgb(207, 220, 235));
		label3.SetBounds(24, 79, 500, 24);
		gradientPanel.Controls.Add(label3);
		status = Ui.Pill("CONNECTING", Color.FromArgb(35, 74, 99), Color.FromArgb(191, 226, 245));
		status.SetBounds(598, 24, 140, 32);
		status.Anchor = AnchorStyles.Top | AnchorStyles.Right;
		gradientPanel.Controls.Add(status);
		unreadHint = Ui.Label("New messages appear automatically", 8, bold: false, Color.FromArgb(190, 207, 225));
		unreadHint.SetBounds(545, 68, 195, 24);
		unreadHint.TextAlign = ContentAlignment.MiddleRight;
		unreadHint.Anchor = AnchorStyles.Top | AnchorStyles.Right;
		gradientPanel.Controls.Add(unreadHint);
		RoundedPanel roundedPanel = Ui.Card(Theme.Surface);
		roundedPanel.SetBounds(18, 142, 768, 420);
		roundedPanel.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
		Controls.Add(roundedPanel);
		messages.SetBounds(12, 12, 744, 396);
		messages.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
		messages.AutoScroll = true;
		messages.FlowDirection = FlowDirection.TopDown;
		messages.WrapContents = false;
		messages.BackColor = Theme.Surface;
		messages.Padding = new Padding(8, 8, 8, 8);
		roundedPanel.Controls.Add(messages);
		RoundedPanel roundedPanel2 = Ui.Card(Theme.Surface);
		roundedPanel2.SetBounds(18, 576, 768, 102);
		roundedPanel2.Anchor = AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
		Controls.Add(roundedPanel2);
		custom.Multiline = true;
		custom.AcceptsReturn = true;
		custom.BorderStyle = BorderStyle.FixedSingle;
		custom.Font = new Font("Segoe UI", 11f);
		custom.BackColor = Theme.Surface2;
		custom.ForeColor = Theme.Text;
		custom.SetBounds(16, 14, 570, 48);
		custom.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
		roundedPanel2.Controls.Add(custom);
		Button button = Ui.Button("SEND", Theme.Blue, 10);
		button.SetBounds(600, 14, 148, 48);
		button.Anchor = AnchorStyles.Top | AnchorStyles.Right;
		roundedPanel2.Controls.Add(button);
		Button button2 = Ui.Button("YES", Theme.Green, 8);
		button2.SetBounds(16, 69, 86, 27);
		roundedPanel2.Controls.Add(button2);
		Button button3 = Ui.Button("NO", Color.FromArgb(83, 98, 119), 8);
		button3.SetBounds(108, 69, 86, 27);
		roundedPanel2.Controls.Add(button3);
		Button button4 = Ui.Button("I'M OKAY", Color.FromArgb(62, 144, 193), 8);
		button4.SetBounds(200, 69, 106, 27);
		roundedPanel2.Controls.Add(button4);
		Button button5 = Ui.Button("CALL ME", Color.FromArgb(108, 102, 178), 8);
		button5.SetBounds(312, 69, 106, 27);
		roundedPanel2.Controls.Add(button5);
		Button button6 = Ui.Button("I NEED HELP", Theme.Danger, 8);
		button6.SetBounds(424, 69, 126, 27);
		roundedPanel2.Controls.Add(button6);
		Label label4 = Ui.Label("Enter sends • Shift+Enter makes a new line", 8, bold: false, Theme.Muted);
		label4.SetBounds(562, 71, 185, 24);
		label4.TextAlign = ContentAlignment.MiddleRight;
		label4.Anchor = AnchorStyles.Top | AnchorStyles.Right;
		roundedPanel2.Controls.Add(label4);
		EventHandler value = (object param0, EventArgs param1) =>
		{
			Send("Yes", "YES");
		};
		button2.Click += value;
		button3.Click += (object param0, EventArgs param1) =>
		{
			Send("No", "NO");
		};
		button4.Click += (object param0, EventArgs param1) =>
		{
			Send("I'm okay", "OKAY");
		};
		button5.Click += (object param0, EventArgs param1) =>
		{
			Send("Call me when you can", "CALL_ME");
		};
		button.Click += (object param0, EventArgs param1) =>
		{
			if (!string.IsNullOrWhiteSpace(custom.Text))
			{
				Send(custom.Text.Trim());
			}
		};
		custom.KeyDown += (object o, KeyEventArgs e) =>
		{
			if (e.KeyCode == Keys.Return && !e.Shift)
			{
				e.SuppressKeyPress = true;
				if (!string.IsNullOrWhiteSpace(custom.Text))
				{
					Send(custom.Text.Trim());
				}
			}
		};
		button6.Click += (object param0, EventArgs param1) =>
		{
			CloudResponse cloudResponse = CloudClient.StartHelp();
			MessageBox.Show((cloudResponse != null && cloudResponse.ok) ? "Your caregivers were notified." : "David could not send the Help request.", "David");
		};
		timer.Interval = 2000;
		timer.Tick += (object param0, EventArgs param1) =>
		{
			RefreshMessages();
		};
		Shown += (object param0, EventArgs param1) =>
		{
			RefreshMessages();
			timer.Start();
			custom.Focus();
		};
		FormClosed += (object param0, FormClosedEventArgs param1) =>
		{
			timer.Stop();
		};
	}

	private void RefreshMessages()
	{
		if (polling)
		{
			return;
		}
		polling = true;
		status.Text = "SYNCING";
		status.BackColor = Color.FromArgb(73, 91, 117);
		ThreadPool.QueueUserWorkItem((object param0) =>
		{
			CloudResponse r = CloudClient.SyncNow();
			try
			{
				BeginInvoke((MethodInvoker)(() =>
				{
					polling = false;
					if (r != null && r.ok)
					{
						status.Text = "LIVE";
						status.BackColor = Color.FromArgb(31, 108, 78);
						Render(r.chat);
					}
					else
					{
						status.Text = "OFFLINE";
						status.BackColor = Color.FromArgb(120, 72, 58);
					}
				}));
			}
			catch
			{
				polling = false;
			}
		});
	}

	private void Render(List<CloudChatMessage> chat)
	{
		List<CloudChatMessage> list = (chat ?? new List<CloudChatMessage>()).Where((CloudChatMessage x) => x != null).ToList();
		CloudChatMessage cloudChatMessage = list.LastOrDefault((CloudChatMessage x) => string.Equals(x.senderKind, "caregiver", StringComparison.OrdinalIgnoreCase));
		replyTargetId = ((cloudChatMessage == null) ? "" : (cloudChatMessage.id ?? ""));
		List<CloudChatMessage> list2 = list.Skip(Math.Max(0, list.Count - 80)).ToList();
		string text = string.Join("|", list2.Select((CloudChatMessage x) => x.id + ":" + x.seenAt));
		if (text == lastRendered && messages.Controls.Count > 0)
		{
			return;
		}
		lastRendered = text;
		messages.SuspendLayout();
		messages.Controls.Clear();
		if (list2.Count == 0)
		{
			RoundedPanel roundedPanel = Ui.Card(Theme.Surface2);
			roundedPanel.Width = 690;
			roundedPanel.Height = 105;
			roundedPanel.Margin = new Padding(12, 60, 12, 8);
			Label label = Ui.Label("No messages yet", 15, bold: true, Theme.Text);
			label.SetBounds(20, 20, 620, 30);
			roundedPanel.Controls.Add(label);
			Label label2 = Ui.Label("Send the first message below. New replies will appear here automatically.", 10, bold: false, Theme.Muted);
			label2.SetBounds(20, 55, 640, 34);
			roundedPanel.Controls.Add(label2);
			messages.Controls.Add(roundedPanel);
		}
		DateTime dateTime = DateTime.MinValue;
		foreach (CloudChatMessage item in list2)
		{
			DateTime.TryParse(item.createdAt, null, DateTimeStyles.RoundtripKind, out var result);
			if (result != DateTime.MinValue)
			{
				result = result.ToLocalTime();
			}
			if (result != DateTime.MinValue && dateTime.Date != result.Date)
			{
				Label label3 = Ui.Label((result.Date == DateTime.Today) ? "TODAY" : result.ToString("dddd, MMM d"), 8, bold: true, Theme.Muted);
				label3.Width = 690;
				label3.Height = 28;
				label3.TextAlign = ContentAlignment.MiddleCenter;
				label3.Margin = new Padding(8, 10, 8, 4);
				messages.Controls.Add(label3);
				dateTime = result;
			}
			bool flag = string.Equals(item.senderKind, "person", StringComparison.OrdinalIgnoreCase);
			string text2 = item.body ?? "";
			using Font font = new Font("Segoe UI", 11f);
			Size size = TextRenderer.MeasureText(text2, font, new Size(430, 2000), TextFormatFlags.TextBoxControl | TextFormatFlags.WordBreak);
			int num = Math.Max(76, size.Height + 54);
			Panel panel = new Panel();
			panel.Width = 690;
			panel.Height = num + 6;
			panel.Margin = new Padding(4, 1, 4, 2);
			panel.BackColor = Color.Transparent;
			RoundedPanel roundedPanel2 = new RoundedPanel();
			roundedPanel2.Radius = 18;
			roundedPanel2.Width = Math.Min(500, Math.Max(225, size.Width + 44));
			roundedPanel2.Height = num;
			roundedPanel2.BackColor = (flag ? Color.FromArgb(34, 103, 83) : Theme.Surface3);
			roundedPanel2.BorderColor = (flag ? Color.FromArgb(48, 139, 111) : Theme.Border);
			roundedPanel2.BorderWidth = 1;
			roundedPanel2.Top = 0;
			roundedPanel2.Left = (flag ? (panel.Width - roundedPanel2.Width - 10) : 10);
			panel.Controls.Add(roundedPanel2);
			string text3;
			if (string.IsNullOrWhiteSpace(item.senderName))
			{
				text3 = (flag ? "You" : "Caregiver");
			}
			else
			{
				text3 = item.senderName;
			}
			Label label4 = Ui.Label(text3, 8, bold: true, flag ? Color.FromArgb(197, 241, 226) : Theme.Muted);
			label4.SetBounds(14, 9, roundedPanel2.Width - 28, 18);
			roundedPanel2.Controls.Add(label4);
			Label label5 = Ui.Label(text2, 11, bold: false, flag ? Color.White : Theme.Text);
			label5.SetBounds(14, 29, roundedPanel2.Width - 28, Math.Max(24, size.Height + 4));
			roundedPanel2.Controls.Add(label5);
			string text4 = ((result == DateTime.MinValue) ? "" : result.ToString("h:mm tt"));
			if (flag)
			{
				text4 += (string.IsNullOrWhiteSpace(item.seenAt) ? "  •  Sent" : "  •  Seen");
			}
			Label label6 = Ui.Label(text4, 7, bold: false, flag ? Color.FromArgb(181, 226, 211) : Theme.Muted);
			label6.TextAlign = (flag ? ContentAlignment.MiddleRight : ContentAlignment.MiddleLeft);
			label6.SetBounds(14, roundedPanel2.Height - 23, roundedPanel2.Width - 28, 17);
			roundedPanel2.Controls.Add(label6);
			messages.Controls.Add(panel);
		}
		messages.ResumeLayout();
		try
		{
			messages.VerticalScroll.Value = messages.VerticalScroll.Maximum;
			messages.PerformLayout();
		}
		catch
		{
		}
	}

	private void Send(string text, string quick = "")
	{
		if (string.IsNullOrWhiteSpace(text) || polling)
		{
			return;
		}
		polling = true;
		status.Text = "SENDING";
		status.BackColor = Color.FromArgb(73, 91, 117);
		string outgoing = text;
		string target = replyTargetId ?? "";
		string quickReply = quick ?? "";
		ThreadPool.QueueUserWorkItem((object param0) =>
		{
			CloudResponse r = CloudClient.ReplyChat(outgoing, target, quickReply);
			try
			{
				BeginInvoke((MethodInvoker)(() =>
				{
					polling = false;
					if (r != null && r.ok)
					{
						custom.Text = "";
						status.Text = "LIVE";
						status.BackColor = Color.FromArgb(31, 108, 78);
						Render(r.chat);
						custom.Focus();
					}
					else
					{
						status.Text = "OFFLINE";
						status.BackColor = Color.FromArgb(120, 72, 58);
						MessageBox.Show((r == null) ? "Message could not be sent." : r.error, "David");
					}
				}));
			}
			catch
			{
				polling = false;
			}
		});
	}
}
