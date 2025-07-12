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
      <div className="min-h-screen bg-gray-100 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
          <p className="text-gray-600">Loading monitor status...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-100 flex items-center justify-center">
        <div className="text-center">
          <div className="text-red-600 text-6xl mb-4">⚠️</div>
          <h2 className="text-2xl font-bold text-gray-900 mb-2">Error</h2>
          <p className="text-gray-600 mb-4">{error}</p>
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
    status: status.primary_status
  };

  const secondaryServer = {
    type: 'secondary',
    name: status.config?.secondary?.name || 'Secondary',
    host: status.config?.secondary?.host || 'Unknown',
    port: status.config?.secondary?.port || 'Unknown',
    status: status.secondary_status
  };

  return (
    <div className="min-h-screen bg-gray-100 py-4">
      <div className="container mx-auto px-4 max-w-6xl">
        {/* Header */}
        <div className="text-center mb-4">
          <h1 className="text-2xl font-bold text-gray-900 mb-2">
            Block Producer Monitor
          </h1>
          <div className="d-flex justify-content-center align-items-center gap-3 text-sm text-gray-600">
            <span>Last updated: {lastUpdate?.toLocaleTimeString()}</span>
            <button onClick={fetchStatus} className="btn btn-primary btn-sm">
              Refresh
            </button>
          </div>
        </div>

        {/* 2x2 Grid Layout */}
        <div className="row g-3">
          {/* Row 1 */}
          <div className="col-12 col-lg-6">
            <ServerCard
              server={primaryServer}
              isActive={status.current_active === 'PRIMARY'}
              onSwitch={handleSwitch}
            />
          </div>
          <div className="col-12 col-lg-6">
            <ServerCard
              server={secondaryServer}
              isActive={status.current_active === 'SECONDARY'}
              onSwitch={handleSwitch}
            />
          </div>
          
          {/* Row 2 */}
          <div className="col-12 col-lg-6">
            <StatusCard
              status={status}
              onStartStop={handleStartStop}
            />
          </div>
          <div className="col-12 col-lg-6">
            <LogsCard status={status} />
          </div>
        </div>
      </div>
    </div>
  );
}