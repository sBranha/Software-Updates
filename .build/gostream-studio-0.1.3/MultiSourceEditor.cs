using System.Text.Json;
using GoStreamStudio.Core;

namespace GoStreamStudio.Controls;

public sealed class MultiSourceEditor : UserControl
{
    private readonly GoStreamController _controller;
    private readonly Action<string> _log;
    private readonly CheckBox _master = new();
    private readonly ComboBox _placeIn = new();
    private readonly ComboBox _background = new();
    private readonly Label _capability = new();
    private readonly MultiSourceCanvas _canvas = new();
    private readonly WindowCard[] _cards = new WindowCard[4];
    private bool _loading;

    public MultiSourceEditor(GoStreamController controller, Action<string> log)
    {
        _controller = controller;
        _log = log;
        Dock = DockStyle.Fill;
        BackColor = Theme.Background;
        BuildUi();
    }

    private void BuildUi()
    {
        var root = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 1,
            RowCount = 3,
            BackColor = Theme.Background,
            Padding = new Padding(4)
        };
        root.RowStyles.Add(new RowStyle(SizeType.Absolute, 58));
        root.RowStyles.Add(new RowStyle(SizeType.Percent, 38));
        root.RowStyles.Add(new RowStyle(SizeType.Percent, 62));
        Controls.Add(root);

        var top = new Panel { Dock = DockStyle.Fill, BackColor = Theme.Surface };
        root.Controls.Add(top, 0, 0);

        _master.Text = "Enable MultiSource";
        _master.AutoSize = true;
        _master.ForeColor = Theme.Text;
        _master.BackColor = Theme.Surface;
        _master.Location = new Point(12, 18);
        _master.CheckedChanged += async (_, _) =>
        {
            if (_loading) return;
            await _controller.SetMultiSourceEnabledAsync(_master.Checked);
            _log($"MultiSource {(_master.Checked ? "enabled" : "disabled")}.");
        };
        top.Controls.Add(_master);

        AddLabel(top, "Place In", 170);
        _placeIn.DropDownStyle = ComboBoxStyle.DropDownList;
        _placeIn.Items.AddRange(new object[] { "Background", "Foreground" });
        _placeIn.SelectedIndex = 0;
        _placeIn.SetBounds(225, 13, 125, 30);
        _placeIn.SelectedIndexChanged += async (_, _) =>
        {
            if (!_loading && _placeIn.SelectedIndex >= 0)
                await _controller.SetMultiSourcePlaceInAsync(_placeIn.SelectedIndex);
        };
        top.Controls.Add(_placeIn);

        AddLabel(top, "Background", 370);
        _background.DropDownStyle = ComboBoxStyle.DropDownList;
        _background.SetBounds(450, 13, 112, 30);
        _background.SelectedIndexChanged += async (_, _) =>
        {
            if (!_loading && _background.SelectedItem is SourceItem s)
                await _controller.SetMultiSourceFillSourceAsync(s.Id);
        };
        top.Controls.Add(_background);

        var save = Button("Save Preset", 585, 12, 110);
        save.Click += (_, _) => SavePreset();
        top.Controls.Add(save);

        var load = Button("Load Preset", 704, 12, 110);
        load.Click += (_, _) => LoadPreset();
        top.Controls.Add(load);

        _capability.AutoSize = true;
        _capability.ForeColor = Theme.Muted;
        _capability.Location = new Point(835, 20);
        top.Controls.Add(_capability);

        _canvas.Dock = DockStyle.Fill;
        _canvas.Margin = new Padding(0, 5, 0, 5);
        root.Controls.Add(_canvas, 0, 1);

        var grid = new TableLayoutPanel
        {
            Dock = DockStyle.Fill,
            ColumnCount = 4,
            RowCount = 1,
            BackColor = Theme.Background
        };
        for (var i = 0; i < 4; i++)
            grid.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 25));

        for (var i = 0; i < 4; i++)
        {
            var index = i;
            _cards[i] = new WindowCard(i, _controller, () => _loading, state =>
            {
                _canvas.SetState(index, state);
                _canvas.Invalidate();
            });
            grid.Controls.Add(_cards[i], i, 0);
        }

        root.Controls.Add(grid, 0, 2);
        _canvas.SetStates(_cards.Select(x => x.State).ToArray());
    }

    public void SetSources(IReadOnlyList<int> ids, int supportedWindows)
    {
        var sources = ids.Count > 0 ? ids.Distinct().ToArray() : Enumerable.Range(1, 12).ToArray();

        _loading = true;
        try
        {
            FillCombo(_background, sources);
            foreach (var card in _cards) card.SetSources(sources);
            for (var i = 0; i < 4; i++) _cards[i].SetSupported(i < supportedWindows);
            _capability.Text = supportedWindows >= 4
                ? "4 MultiSource windows available"
                : "Window 1 + Window 2 available on this model";
        }
        finally
        {
            _loading = false;
        }
    }

    private static void FillCombo(ComboBox combo, IReadOnlyList<int> ids)
    {
        var keep = combo.SelectedItem is SourceItem old ? old.Id : 0;
        combo.Items.Clear();
        foreach (var id in ids) combo.Items.Add(new SourceItem(id));
        if (combo.Items.Count == 0) return;

        var match = 0;
        for (var i = 0; i < combo.Items.Count; i++)
            if (combo.Items[i] is SourceItem s && s.Id == keep) { match = i; break; }
        combo.SelectedIndex = match;
    }

    private void SavePreset()
    {
        using var dialog = new SaveFileDialog
        {
            Title = "Save MultiSource Preset",
            Filter = "GoStream MultiSource Preset (*.gms)|*.gms",
            DefaultExt = "gms",
            AddExtension = true
        };
        if (dialog.ShowDialog(this) != DialogResult.OK) return;

        var preset = new MultiSourcePreset
        {
            Enabled = _master.Checked,
            PlaceIn = Math.Max(0, _placeIn.SelectedIndex),
            Background = (_background.SelectedItem as SourceItem)?.Id ?? 1,
            Windows = _cards.Select(x => x.State.Clone()).ToArray()
        };

        File.WriteAllText(dialog.FileName,
            JsonSerializer.Serialize(preset, new JsonSerializerOptions { WriteIndented = true }));
        _log("MultiSource preset saved.");
    }

    private async void LoadPreset()
    {
        using var dialog = new OpenFileDialog
        {
            Title = "Load MultiSource Preset",
            Filter = "GoStream MultiSource Preset (*.gms)|*.gms|All files (*.*)|*.*"
        };
        if (dialog.ShowDialog(this) != DialogResult.OK) return;

        try
        {
            var preset = JsonSerializer.Deserialize<MultiSourcePreset>(File.ReadAllText(dialog.FileName));
            if (preset is null) return;

            _loading = true;
            try
            {
                _master.Checked = preset.Enabled;
                _placeIn.SelectedIndex = Math.Clamp(preset.PlaceIn, 0, 1);
                SelectSource(_background, preset.Background);
                for (var i = 0; i < Math.Min(4, preset.Windows.Length); i++)
                    _cards[i].LoadState(preset.Windows[i]);
            }
            finally
            {
                _loading = false;
            }

            await _controller.SetMultiSourceEnabledAsync(_master.Checked);
            await _controller.SetMultiSourcePlaceInAsync(Math.Max(0, _placeIn.SelectedIndex));
            if (_background.SelectedItem is SourceItem bg)
                await _controller.SetMultiSourceFillSourceAsync(bg.Id);

            foreach (var card in _cards.Where(x => x.Supported))
                await card.ApplyAllAsync();

            _canvas.SetStates(_cards.Select(x => x.State).ToArray());
            _canvas.Invalidate();
            _log("MultiSource preset loaded and applied.");
        }
        catch (Exception ex)
        {
            MessageBox.Show(this, "Could not load preset: " + ex.Message,
                "GoStream Studio", MessageBoxButtons.OK, MessageBoxIcon.Warning);
        }
    }

    private static void SelectSource(ComboBox combo, int id)
    {
        for (var i = 0; i < combo.Items.Count; i++)
            if (combo.Items[i] is SourceItem s && s.Id == id) { combo.SelectedIndex = i; return; }
    }

    private static void AddLabel(Control parent, string text, int x)
    {
        parent.Controls.Add(new Label
        {
            Text = text,
            ForeColor = Theme.Muted,
            AutoSize = true,
            Location = new Point(x, 20)
        });
    }

    private static Button Button(string text, int x, int y, int width) => new()
    {
        Text = text,
        Location = new Point(x, y),
        Width = width,
        Height = 32,
        FlatStyle = FlatStyle.Flat,
        BackColor = Theme.Surface2,
        ForeColor = Theme.Text,
        FlatAppearance = { BorderColor = Theme.Border, BorderSize = 1 }
    };

    private sealed record SourceItem(int Id)
    {
        public override string ToString() => $"CAM {Id}";
    }

    public sealed class MultiSourcePreset
    {
        public bool Enabled { get; set; }
        public int PlaceIn { get; set; }
        public int Background { get; set; } = 1;
        public WindowState[] Windows { get; set; } = Array.Empty<WindowState>();
    }

    public sealed class WindowState
    {
        public bool Enabled { get; set; } = true;
        public int Source { get; set; } = 1;
        public decimal X { get; set; }
        public decimal Y { get; set; }
        public decimal Size { get; set; } = 0.45m;
        public decimal CropL { get; set; }
        public decimal CropR { get; set; }
        public decimal CropT { get; set; }
        public decimal CropB { get; set; }
        public decimal Corner { get; set; }
        public decimal Border { get; set; }

        public WindowState Clone() => (WindowState)MemberwiseClone();
    }

    private sealed class WindowCard : Panel
    {
        private readonly int _index;
        private readonly GoStreamController _controller;
        private readonly Func<bool> _loading;
        private readonly Action<WindowState> _changed;
        private readonly CheckBox _enabled = new();
        private readonly ComboBox _source = new();
        private readonly Dictionary<string, NumericUpDown> _n = new();
        private readonly Label _unsupported = new();

        public WindowState State { get; private set; } = new();
        public bool Supported { get; private set; } = true;

        public WindowCard(int index, GoStreamController controller, Func<bool> loading, Action<WindowState> changed)
        {
            _index = index;
            _controller = controller;
            _loading = loading;
            _changed = changed;
            Dock = DockStyle.Fill;
            Margin = new Padding(4);
            Padding = new Padding(6);
            BackColor = Theme.Surface;
            Build();
            Paint += (_, e) =>
            {
                using var pen = new Pen(Theme.Border);
                e.Graphics.DrawRectangle(pen, 0, 0, Width - 1, Height - 1);
            };
        }

        private void Build()
        {
            Controls.Add(new Label
            {
                Text = $"WINDOW {_index + 1}",
                ForeColor = Color.White,
                Font = new Font("Segoe UI Semibold", 9.5f),
                AutoSize = true,
                Location = new Point(8, 8)
            });

            _enabled.Text = "Enable";
            _enabled.Checked = true;
            _enabled.AutoSize = true;
            _enabled.ForeColor = Theme.Text;
            _enabled.BackColor = Theme.Surface;
            _enabled.Location = new Point(95, 8);
            _enabled.CheckedChanged += async (_, _) =>
            {
                State.Enabled = _enabled.Checked;
                Notify();
                if (!_loading() && Supported)
                    await _controller.SetMultiSourceWindowEnabledAsync(_index, State.Enabled);
            };
            Controls.Add(_enabled);

            Controls.Add(Label("Source", 8, 40));
            _source.DropDownStyle = ComboBoxStyle.DropDownList;
            _source.SetBounds(58, 36, 104, 28);
            _source.SelectedIndexChanged += async (_, _) =>
            {
                if (_source.SelectedItem is SourceItem s)
                {
                    State.Source = s.Id;
                    Notify();
                    if (!_loading() && Supported)
                        await _controller.SetMultiSourceWindowSourceAsync(_index, s.Id);
                }
            };
            Controls.Add(_source);

            var reset = Button("Reset", 170, 35, 58);
            reset.Height = 29;
            reset.Click += async (_, _) =>
            {
                var source = State.Source;
                LoadState(new WindowState { Source = source, Enabled = State.Enabled });
                if (Supported) await ApplyAllAsync();
            };
            Controls.Add(reset);

            var y = 74;
            Pair("X", "Y", ref y, -1m, 1m, .01m, 2);
            Pair("Size", "Corner", ref y, 0m, 2m, .01m, 2);
            Pair("Crop L", "Crop R", ref y, 0m, 100m, 1m, 0);
            Pair("Crop T", "Crop B", ref y, 0m, 100m, 1m, 0);
            Pair("Border", "", ref y, 0m, 100m, 1m, 0);

            _n["Size"].Value = .45m;

            foreach (var pair in _n)
            {
                var key = pair.Key;
                pair.Value.ValueChanged += async (_, _) =>
                {
                    PullState();
                    Notify();
                    if (!_loading() && Supported) await SendAsync(key, pair.Value.Value);
                };
            }

            _unsupported.Text = "Not available\non this model";
            _unsupported.ForeColor = Theme.Muted;
            _unsupported.BackColor = Theme.Surface;
            _unsupported.Font = new Font("Segoe UI Semibold", 10f);
            _unsupported.TextAlign = ContentAlignment.MiddleCenter;
            _unsupported.Dock = DockStyle.Fill;
            _unsupported.Visible = false;
            Controls.Add(_unsupported);
        }

        private void Pair(string a, string b, ref int y, decimal min, decimal max, decimal step, int decimals)
        {
            Controls.Add(Label(a, 8, y));
            var left = Num(56, y, min, max, step, decimals);
            _n[a] = left;
            Controls.Add(left);

            if (!string.IsNullOrEmpty(b))
            {
                Controls.Add(Label(b, 124, y));
                var right = Num(174, y, min, max, step, decimals);
                _n[b] = right;
                Controls.Add(right);
            }
            y += 34;
        }

        private static Label Label(string text, int x, int y) => new()
        {
            Text = text,
            ForeColor = Theme.Muted,
            AutoSize = true,
            Font = new Font("Segoe UI", 8f),
            Location = new Point(x, y + 5)
        };

        private static NumericUpDown Num(int x, int y, decimal min, decimal max, decimal step, int decimals) => new()
        {
            Location = new Point(x, y),
            Width = 62,
            Minimum = min,
            Maximum = max,
            Increment = step,
            DecimalPlaces = decimals,
            BackColor = Color.FromArgb(13, 28, 39),
            ForeColor = Theme.Text,
            BorderStyle = BorderStyle.FixedSingle
        };

        public void SetSources(IReadOnlyList<int> ids)
        {
            var keep = State.Source;
            _source.Items.Clear();
            foreach (var id in ids) _source.Items.Add(new SourceItem(id));
            if (_source.Items.Count == 0) return;
            var match = 0;
            for (var i = 0; i < _source.Items.Count; i++)
                if (_source.Items[i] is SourceItem s && s.Id == keep) { match = i; break; }
            _source.SelectedIndex = match;
        }

        public void SetSupported(bool supported)
        {
            Supported = supported;
            _unsupported.Visible = !supported;
            if (!supported) _unsupported.BringToFront();
        }

        public void LoadState(WindowState state)
        {
            State = state.Clone();
            _enabled.Checked = State.Enabled;
            SelectSource(State.Source);
            Set("X", State.X);
            Set("Y", State.Y);
            Set("Size", State.Size);
            Set("Corner", State.Corner);
            Set("Crop L", State.CropL);
            Set("Crop R", State.CropR);
            Set("Crop T", State.CropT);
            Set("Crop B", State.CropB);
            Set("Border", State.Border);
            Notify();
        }

        private void SelectSource(int id)
        {
            for (var i = 0; i < _source.Items.Count; i++)
                if (_source.Items[i] is SourceItem s && s.Id == id) { _source.SelectedIndex = i; return; }
        }

        private void Set(string key, decimal value)
        {
            if (_n.TryGetValue(key, out var n)) n.Value = Math.Clamp(value, n.Minimum, n.Maximum);
        }

        private void PullState()
        {
            State.X = _n["X"].Value;
            State.Y = _n["Y"].Value;
            State.Size = _n["Size"].Value;
            State.Corner = _n["Corner"].Value;
            State.CropL = _n["Crop L"].Value;
            State.CropR = _n["Crop R"].Value;
            State.CropT = _n["Crop T"].Value;
            State.CropB = _n["Crop B"].Value;
            State.Border = _n["Border"].Value;
        }

        private void Notify() => _changed(State.Clone());

        private Task SendAsync(string key, decimal value) => key switch
        {
            "X" => _controller.SetMultiSourceWindowXAsync(_index, value),
            "Y" => _controller.SetMultiSourceWindowYAsync(_index, value),
            "Size" => _controller.SetMultiSourceWindowSizeAsync(_index, value),
            "Corner" => _controller.SetMultiSourceWindowCornerRadiusAsync(_index, value),
            "Crop L" => _controller.SetMultiSourceWindowCropLeftAsync(_index, value),
            "Crop R" => _controller.SetMultiSourceWindowCropRightAsync(_index, value),
            "Crop T" => _controller.SetMultiSourceWindowCropTopAsync(_index, value),
            "Crop B" => _controller.SetMultiSourceWindowCropBottomAsync(_index, value),
            "Border" => _controller.SetMultiSourceWindowBorderWidthAsync(_index, value),
            _ => Task.CompletedTask
        };

        public async Task ApplyAllAsync()
        {
            PullState();
            await _controller.SetMultiSourceWindowEnabledAsync(_index, State.Enabled);
            await _controller.SetMultiSourceWindowSourceAsync(_index, State.Source);
            await _controller.SetMultiSourceWindowXAsync(_index, State.X);
            await _controller.SetMultiSourceWindowYAsync(_index, State.Y);
            await _controller.SetMultiSourceWindowSizeAsync(_index, State.Size);
            await _controller.SetMultiSourceWindowCornerRadiusAsync(_index, State.Corner);
            await _controller.SetMultiSourceWindowCropLeftAsync(_index, State.CropL);
            await _controller.SetMultiSourceWindowCropRightAsync(_index, State.CropR);
            await _controller.SetMultiSourceWindowCropTopAsync(_index, State.CropT);
            await _controller.SetMultiSourceWindowCropBottomAsync(_index, State.CropB);
            await _controller.SetMultiSourceWindowBorderWidthAsync(_index, State.Border);
        }
    }

    private sealed class MultiSourceCanvas : Panel
    {
        private WindowState[] _states = Enumerable.Range(0, 4).Select(_ => new WindowState()).ToArray();

        public MultiSourceCanvas()
        {
            DoubleBuffered = true;
            BackColor = Theme.Surface;
        }

        public void SetStates(WindowState[] states) => _states = states.Select(s => s.Clone()).ToArray();

        public void SetState(int index, WindowState state)
        {
            if (index >= 0 && index < _states.Length) _states[index] = state.Clone();
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            base.OnPaint(e);
            var pad = 18;
            var area = new Rectangle(pad, pad, Math.Max(20, Width - pad * 2), Math.Max(20, Height - pad * 2));
            var w = area.Width;
            var h = (int)(w * 9.0 / 16.0);
            if (h > area.Height) { h = area.Height; w = (int)(h * 16.0 / 9.0); }
            var frame = new Rectangle(area.X + (area.Width - w) / 2, area.Y + (area.Height - h) / 2, w, h);

            using var bg = new SolidBrush(Color.FromArgb(5, 12, 18));
            using var border = new Pen(Theme.Border, 2);
            e.Graphics.FillRectangle(bg, frame);
            e.Graphics.DrawRectangle(border, frame);

            var colors = new[]
            {
                Color.FromArgb(70, 190, 255),
                Color.FromArgb(255, 170, 65),
                Color.FromArgb(195, 100, 245),
                Color.FromArgb(90, 215, 130)
            };

            for (var i = 0; i < 4; i++)
            {
                var s = _states[i];
                if (!s.Enabled) continue;
                var size = (double)Math.Clamp(s.Size, .08m, 1m);
                var rw = (int)(frame.Width * size);
                var rh = (int)(rw * 9.0 / 16.0);
                var cx = frame.Left + frame.Width / 2.0 + (double)s.X * frame.Width / 2.0;
                var cy = frame.Top + frame.Height / 2.0 - (double)s.Y * frame.Height / 2.0;
                var r = new Rectangle((int)(cx - rw / 2.0), (int)(cy - rh / 2.0), rw, rh);

                using var fill = new SolidBrush(Color.FromArgb(45, colors[i]));
                using var pen = new Pen(colors[i], 2);
                e.Graphics.FillRectangle(fill, r);
                e.Graphics.DrawRectangle(pen, r);
                using var font = new Font("Segoe UI Semibold", 9f);
                using var brush = new SolidBrush(Color.White);
                e.Graphics.DrawString($"W{i + 1}  CAM {s.Source}", font, brush, r.X + 5, r.Y + 4);
            }
        }
    }
}
