#ifndef ANDROIDDEVICELISTER_H
#define ANDROIDDEVICELISTER_H

#include <medialibrary/IDeviceLister.h>
#include <string>
#include <vector>
#include <unordered_map>
#include <mutex>

class AndroidDeviceLister : public medialibrary::IDeviceLister
{
public:
    std::vector<std::tuple<std::string, std::string, bool>> devices() const;
    void addDevice(std::string, std::string, bool);
    bool removeDevice(std::string uuidToRemove, const std::string& path);
    void refresh();
    bool start( medialibrary::IDeviceListerCb* cb );
    void stop();

private:
    std::unordered_map<std::string, std::tuple<std::string, std::string, bool>> m_devices;

private:
    mutable std::mutex m_mutex;
    medialibrary::IDeviceListerCb* p_DeviceListerCb = nullptr;
};

#endif // ANDROIDDEVICELISTER_H
