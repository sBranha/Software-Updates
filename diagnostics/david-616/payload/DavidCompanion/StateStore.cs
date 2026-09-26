using System;
using System.Collections.Generic;

namespace DavidCompanion;

[Serializable]
public class StateStore
{
	public List<StateEntry> Items { get; set; }

	public StateStore()
	{
		Items = new List<StateEntry>();
	}
}
