using System;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;

namespace DavidCompanion;

public class HistoryForm : Form
{
	public HistoryForm()
	{
		Text = "David - Reminder History";
		StartPosition = FormStartPosition.CenterParent;
		Size = new Size(930, 560);
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		DataGridView dataGridView = new DataGridView
		{
			Dock = DockStyle.Fill,
			ReadOnly = true,
			AllowUserToAddRows = false,
			AutoSizeColumnsMode = DataGridViewAutoSizeColumnsMode.Fill,
			BackgroundColor = Theme.Surface,
			BorderStyle = BorderStyle.None,
			RowHeadersVisible = false
		};
		Controls.Add(dataGridView);
		DataTable dataTable = new DataTable
		{
			Columns = { "When", "Reminder", "Action", "Scheduled For" }
		};
		foreach (HistoryEntry item in AppData.LoadHistory().Items.OrderByDescending((HistoryEntry x) => x.Timestamp))
		{
			string text = (DateTime.TryParse(item.Timestamp, out var result) ? result.ToString("g") : item.Timestamp);
			string text2 = (DateTime.TryParse(item.ScheduledFor, out var result2) ? result2.ToString("g") : item.ScheduledFor);
			dataTable.Rows.Add(text, item.Title, item.Action, text2);
		}
		dataGridView.DataSource = dataTable;
	}
}
