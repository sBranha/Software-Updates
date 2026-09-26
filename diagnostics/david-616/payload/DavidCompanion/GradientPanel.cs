using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Windows.Forms;

namespace DavidCompanion;

public class GradientPanel : Panel
{
	public Color Color1 = Color.FromArgb(13, 35, 63);

	public Color Color2 = Color.FromArgb(36, 73, 111);

	public int Radius = 24;

	public GradientPanel()
	{
		DoubleBuffered = true;
		EventHandler value = (object param0, EventArgs param1) =>
		{
			UpdateRegion();
		};
		Resize += value;
	}

	private void UpdateRegion()
	{
		if (Width < 2 || Height < 2)
		{
			return;
		}
		using GraphicsPath path = Ui.RoundPath(new Rectangle(0, 0, Width, Height), Radius);
		Region = new Region(path);
	}

	protected override void OnPaintBackground(PaintEventArgs e)
	{
		e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
		using LinearGradientBrush brush = new LinearGradientBrush(ClientRectangle, Color1, Color2, 18f);
		e.Graphics.FillRectangle(brush, ClientRectangle);
	}

	protected override void OnPaint(PaintEventArgs e)
	{
		base.OnPaint(e);
		e.Graphics.SmoothingMode = SmoothingMode.AntiAlias;
		using Pen pen = new Pen(Color.FromArgb(55, 115, 166), 1f);
		using GraphicsPath path = Ui.RoundPath(new Rectangle(0, 0, Width - 1, Height - 1), Radius);
		e.Graphics.DrawPath(pen, path);
	}
}
