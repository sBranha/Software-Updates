namespace GoStreamStudio.Controls;

public sealed class WorkspaceForm : Form
{
    private readonly System.Windows.Forms.Timer _statusTimer = new();
    private readonly Label _status = new();
    private readonly Func<string>? _statusProvider;

    public WorkspaceForm(string title, Control content, Func<string>? statusProvider = null)
    {
        _statusProvider = statusProvider;

        Text = $"GoStream Studio — {title}";
        BackColor = Theme.Background;
        ForeColor = Theme.Text;
        StartPosition = FormStartPosition.CenterParent;
        WindowState = FormWindowState.Maximized;
        MinimumSize = new Size(1100, 700);
        KeyPreview = true;

        var root = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 1,
            RowCount = 2,
            BackColor = Theme.Background
        };
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 62));
        root.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        Controls.Add(root);

        var header = new Panel
        {
            Dock = DockStyle.Fill,
            BackColor = Color.FromArgb(7, 27, 40)
        };
        root.Controls.Add(header, 0, 0);

        var titleLabel = new Label
        {
            Text = title,
            ForeColor = Color.White,
            Font = new Font("Segoe UI Semibold", 16f),
            AutoSize = true,
            Location = new Point(18, 16)
        };
        header.Controls.Add(titleLabel);

        _status.Text = "Waiting for device";
        _status.ForeColor = Theme.Muted;
        _status.Font = new Font("Segoe UI", 9f);
        _status.AutoSize = true;
        _status.Location = new Point(300, 22);
        header.Controls.Add(_status);

        var close = new Button
        {
            Text = "BACK TO SWITCHER",
            Width = 170,
            Height = 36,
            Anchor = AnchorStyles.Top | AnchorStyles.Right,
            FlatStyle = FlatStyle.Flat,
            BackColor = Theme.Blue,
            ForeColor = Color.White,
            Font = new Font("Segoe UI Semibold", 9f),
            Location = new Point(Math.Max(0, ClientSize.Width - 190), 13)
        };
        close.FlatAppearance.BorderColor = Theme.Blue;
        close.Click += (_, _) => Close();
        header.Controls.Add(close);

        header.Resize += (_, _) =>
            close.Location = new Point(Math.Max(0, header.ClientSize.Width - close.Width - 18), 13);

        var host = new Panel
        {
            Dock = DockStyle.Fill,
            BackColor = Theme.Background,
            Padding = new Padding(12)
        };
        content.Dock = DockStyle.Fill;
        host.Controls.Add(content);
        root.Controls.Add(host, 0, 1);

        _statusTimer.Interval = 500;
        _statusTimer.Tick += (_, _) => UpdateStatus();
        _statusTimer.Start();

        KeyDown += (_, e) =>
        {
            if (e.KeyCode == Keys.Escape)
            {
                Close();
                e.Handled = true;
            }
        };

        FormClosed += (_, _) => _statusTimer.Stop();
        Shown += (_, _) => UpdateStatus();
    }

    private void UpdateStatus()
    {
        if (_statusProvider is null) return;
        try { _status.Text = _statusProvider(); }
        catch { _status.Text = "Device status unavailable"; }
    }
}
