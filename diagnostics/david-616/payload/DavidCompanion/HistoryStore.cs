using System;
using System.Collections.Generic;

namespace DavidCompanion;

[Serializable]
public class HistoryStore
{
	public List<HistoryEntry> Items { get; set; }

	public HistoryStore()
	{
		Items = new List<HistoryEntry>();
	}
}
