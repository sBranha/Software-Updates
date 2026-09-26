using System;

namespace DavidCompanion;

[Serializable]
public class StateEntry
{
	public string Key { get; set; }

	public string Status { get; set; }

	public string Updated { get; set; }

	public string Until { get; set; }
}
