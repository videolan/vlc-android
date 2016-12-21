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
    bool removeDevice(std::string uuidToRemove);

private:
    std::unordered_map<std::string, std::tuple<std::string, std::string, bool>> m_devices;

private:
    mutable std::mutex m_mutex;
};

#endif // ANDROIDDEVICELISTER_H
