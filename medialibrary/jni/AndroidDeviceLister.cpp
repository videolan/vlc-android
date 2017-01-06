#include "AndroidDeviceLister.h"

std::vector<std::tuple<std::string, std::string, bool>>
AndroidDeviceLister::devices() const
{
    std::lock_guard<std::mutex> guard(m_mutex);
    std::vector<std::tuple<std::string, std::string, bool>> devices;
    devices.reserve( m_devices.size() );
    for(auto kv : m_devices)
        devices.push_back(kv.second);
    return devices;
}

void
AndroidDeviceLister::addDevice(std::string uuid, std::string path, bool removable)
{
    std::lock_guard<std::mutex> guard(m_mutex);
    m_devices.insert(std::make_pair(uuid, std::make_tuple(uuid, path, removable)));
}

bool
AndroidDeviceLister::removeDevice(std::string uuidToRemove)
{
    std::lock_guard<std::mutex> guard(m_mutex);
    return m_devices.erase(uuidToRemove);
}
