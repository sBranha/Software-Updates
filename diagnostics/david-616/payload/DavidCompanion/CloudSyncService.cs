using System;
using System.Threading;

namespace DavidCompanion;

public static class CloudSyncService
{
	private static int Running;

	public static void SyncAsync()
	{
		if (!CloudClient.IsConnected() || Interlocked.Exchange(ref Running, 1) == 1)
		{
			return;
		}
		ThreadPool.QueueUserWorkItem((object param0) =>
		{
			try
			{
				CloudClient.SyncNow();
			}
			catch
			{
			}
			finally
			{
				Interlocked.Exchange(ref Running, 0);
			}
		});
	}

	public static void SaveReminderAsync(Reminder reminder)
	{
		if (!CloudClient.IsConnected() || reminder == null)
		{
			return;
		}
		ThreadPool.QueueUserWorkItem((object param0) =>
		{
			try
			{
				CloudClient.SaveReminder(reminder);
			}
			catch
			{
			}
		});
	}

	public static void DeleteReminderAsync(string reminderId)
	{
		if (!CloudClient.IsConnected() || string.IsNullOrWhiteSpace(reminderId))
		{
			return;
		}
		ThreadPool.QueueUserWorkItem((object param0) =>
		{
			try
			{
				CloudClient.DeleteReminder(reminderId);
			}
			catch
			{
			}
		});
	}

	public static void MarkOccurrenceDoneAsync(string occurrenceKey, string reminderId, DateTime scheduled)
	{
		if (!CloudClient.IsConnected() || string.IsNullOrWhiteSpace(occurrenceKey))
		{
			return;
		}
		ThreadPool.QueueUserWorkItem((object _) =>
		{
			try
			{
				CloudClient.MarkOccurrenceDoneRemote(occurrenceKey, reminderId, scheduled);
			}
			catch
			{
			}
		});
	}
}
