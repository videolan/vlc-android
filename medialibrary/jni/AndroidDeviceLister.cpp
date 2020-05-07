#include "AndroidDeviceLister.h"

std::vector<std::tuple<std::string, std::string, bool>>
AndroidDeviceLister::devices() const
{
    std::lock_guard<std::mutex> guard(m_mutex);
    std::vector<std::tuple<std::string, std::string, bool>> devices;
    devices.reserve( m_devices.size() );
    for(auto kv : m_devices) devices.push_back(kv.second);
    return devices;
}

void
AndroidDeviceLister::addDevice(std::string uuid, std::string path, bool removable)
{
    std::lock_guard<std::mutex> guard(m_mutex);
    m_devices.insert(std::make_pair(uuid, std::make_tuple(uuid, path, removable)));
    if (p_DeviceListerCb != nullptr)
        p_DeviceListerCb->onDeviceMounted(uuid, path, removable);
}

bool
AndroidDeviceLister::removeDevice(std::string uuidToRemove, const std::string& path)
{
    std::lock_guard<std::mutex> guard(m_mutex);
    auto iterator = m_devices.erase(uuidToRemove);
    auto erased = iterator > 0;
    if (erased && p_DeviceListerCb != nullptr) p_DeviceListerCb->onDeviceUnmounted(uuidToRemove, path);
    return erased;
}

bool AndroidDeviceLister::start( medialibrary::IDeviceListerCb* cb )
{
    p_DeviceListerCb = cb;
    return true;
}

void AndroidDeviceLister::stop()
{

}

void AndroidDeviceLister::refresh()
{
    if (p_DeviceListerCb == nullptr) return;
    std::string uuid;
    std::string path;
    bool removable;
    for (auto d : m_devices ) {
        std::tie(uuid, path, removable) = d.second;
        p_DeviceListerCb->onDeviceMounted(uuid, path, removable);
    }
}
