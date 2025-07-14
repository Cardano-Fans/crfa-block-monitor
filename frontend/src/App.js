import React, { useState, useEffect } from 'react';
import axios from 'axios';
import ServerCard from './components/ServerCard';
import StatusCard from './components/StatusCard';
import LogsCard from './components/LogsCard';

export default function App() {
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [lastUpdate, setLastUpdate] = useState(null);

  const fetchStatus = async () => {
    try {
      const response = await axios.get('/api/status');
      setStatus(response.data.monitor);
      setLastUpdate(new Date());
      setError(null);
    } catch (err) {
      setError('Failed to fetch status');
      console.error('Error fetching status:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSwitch = async (serverType) => {
    try {
      const response = await axios.post('/api/active', { active: serverType.toUpperCase() });
      if (response.data.success) {
        await fetchStatus();
        alert(response.data.message);
      } else {
        alert('Error: ' + response.data.message);
      }
    } catch (err) {
      alert('Failed to switch server: ' + err.message);
    }
  };

  const handleStartStop = async (action) => {
    try {
      const response = await axios.post('/api/control', { action: action.toUpperCase() });
      if (response.data.success) {
        await fetchStatus();
        alert(response.data.message);
      } else {
        alert('Error: ' + response.data.message);
      }
    } catch (err) {
      alert('Failed to control daemon: ' + err.message);
    }
  };


  useEffect(() => {
    fetchStatus();
    
    const interval = setInterval(fetchStatus, 30000);
    
    return () => clearInterval(interval);
  }, []);

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="loading-spinner mx-auto mb-4"></div>
          <p className="text-lg" style={{ color: 'var(--text-secondary)' }}>Loading monitor status...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="text-6xl mb-4">⚠️</div>
          <h2 className="text-2xl font-bold mb-2" style={{ color: 'var(--text-primary)' }}>Error</h2>
          <p className="mb-4" style={{ color: 'var(--text-secondary)' }}>{error}</p>
          <button
            onClick={fetchStatus}
            className="btn btn-primary"
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  const primaryServer = {
    type: 'primary',
    name: status.config?.primary?.name || 'Primary',
    host: status.config?.primary?.host || 'Unknown',
    port: status.config?.primary?.port || 'Unknown',
    status: status.primary_status?.toLowerCase() || 'unknown'
  };

  const secondaryServer = {
    type: 'secondary',
    name: status.config?.secondary?.name || 'Secondary',
    host: status.config?.secondary?.host || 'Unknown',
    port: status.config?.secondary?.port || 'Unknown',
    status: status.secondary_status?.toLowerCase() || 'unknown'
  };

  return (
    <div className="min-h-screen py-8">
      <div className="header">
        <div className="container">
          <h1 className="h1-example">
            Block Producer Monitor
          </h1>
          <p className="body-example">
            Real-time monitoring and failover management for Cardano infrastructure
          </p>
          <div className="flex justify-center items-center gap-4 mt-4">
            <span className="small-example">
              Last updated: {lastUpdate?.toLocaleTimeString()}
            </span>
            <button onClick={fetchStatus} className="btn btn-primary btn-sm">
              🔄 Refresh
            </button>
          </div>
        </div>
      </div>

      <div className="container">
        {/* 2x2 Grid Layout */}
        <div className="grid grid-cols-2">
          <ServerCard
            server={primaryServer}
            isActive={status.current_active === 'PRIMARY'}
            onSwitch={handleSwitch}
          />
          <ServerCard
            server={secondaryServer}
            isActive={status.current_active === 'SECONDARY'}
            onSwitch={handleSwitch}
          />
          <StatusCard
            status={status}
            onStartStop={handleStartStop}
          />
          <LogsCard status={status} />
        </div>
      </div>
    </div>
  );
}